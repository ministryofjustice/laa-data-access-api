package uk.gov.justice.laa.dstew.access.controller;

import lombok.Getter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.dstew.access.model.*;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.HeaderUtils;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.builders.ProblemDetailBuilder;
import uk.gov.justice.laa.dstew.access.utils.builders.ValidationExceptionBuilder;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.*;

@ActiveProfiles("test")
public class ApplicationTest extends BaseIntegrationTest {
    private static final int applicationVersion = 1;

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class GetApplication {

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void givenExistingApplication_whenGetApplication_thenReturnOKWithCorrectData() throws Exception {
            // given
            ApplicationEntity application = persistedApplicationFactory.createAndPersist();
            Application expectedApplication = createApplication(application);

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, expectedApplication.getId());
            Application actualApplication = deserialise(result, Application.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertThat(actualApplication).isEqualTo(expectedApplication);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void givenApplicationNotExist_whenGetApplication_thenReturnNotFound() throws Exception {
            // given
            persistedApplicationFactory.createAndPersist();
            UUID notExistApplicationId = UUID.randomUUID();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, notExistApplicationId);

            // then
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertNotFound(result);
            assertEquals("application/problem+json", result.getResponse().getContentType());
            ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
            assertEquals("No application found with id: " + notExistApplicationId, problemDetail.getDetail());

        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
        public void givenUnknownRole_whenGetApplication_thenReturnForbidden() throws Exception {
            // given
            ApplicationEntity expectedApplication = persistedApplicationFactory.createAndPersist();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, expectedApplication.getId());

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }

        @Test
        public void givenNoUser_whenGetApplication_thenReturnUnauthorised() throws Exception {
            // given
            ApplicationEntity expectedApplication = persistedApplicationFactory.createAndPersist();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, expectedApplication.getId());

            // then
            assertSecurityHeaders(result);
            assertUnauthorised(result);
        }

        private Application createApplication(ApplicationEntity applicationEntity) {
            Application application = new Application();
            application.setId(applicationEntity.getId());
            application.setApplicationContent(applicationEntity.getApplicationContent());
            application.setApplicationStatus(applicationEntity.getStatus());
            application.setSchemaVersion(applicationEntity.getSchemaVersion());
            if (applicationEntity.getCaseworker() != null) {
                application.setCaseworkerId(applicationEntity.getCaseworker().getId());
            }
            if (applicationEntity.getIndividuals() != null) {
                List<Individual> individuals = applicationEntity.getIndividuals().stream()
                        .map(individualEntity -> {
                            Individual individual = new Individual();
                            individual.setFirstName(individualEntity.getFirstName());
                            individual.setLastName(individualEntity.getLastName());
                            individual.setDateOfBirth(individualEntity.getDateOfBirth());
                            individual.setDetails(individualEntity.getIndividualContent());
                            return individual;
                        })
                        .collect(Collectors.toList());
                application.setIndividuals(individuals);
            }
            application.setCreatedAt(OffsetDateTime.ofInstant(applicationEntity.getCreatedAt(), ZoneOffset.UTC));
            application.setUpdatedAt(OffsetDateTime.ofInstant(applicationEntity.getUpdatedAt(), ZoneOffset.UTC));
            return application;
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class CreateApplication {

        @Test
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenCreateNewApplication_whenCreateApplication_thenReturnCreatedWithLocationHeader() throws Exception {

            // given
            ApplicationCreateRequest applicationCreateRequest = applicationCreateRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, applicationCreateRequest);

            // then
            assertSecurityHeaders(result);
            assertCreated(result);

            UUID createdApplicationId = HeaderUtils.GetUUIDFromLocation(
                    result.getResponse().getHeader("Location")
            );
            assertNotNull(createdApplicationId);
            ApplicationEntity createdApplication = applicationRepository.findById(createdApplicationId).orElseThrow(() -> new ApplicationNotFoundException(createdApplicationId.toString()));
            assertApplicationEqual(applicationCreateRequest, createdApplication);
        }

        @ParameterizedTest
        @MethodSource("applicationCreateRequestInvalidDataCases")
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenInvalidApplicationRequestData_whenCreateApplication_thenReturnBadRequest(
                ApplicationCreateRequest request,
                ProblemDetail expectedDetail) throws Exception {
            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);
            ProblemDetail detail = deserialise(result, ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            assertProblemRecord(HttpStatus.BAD_REQUEST, expectedDetail, result, detail);
            assertEquals(0, applicationRepository.count());
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenInvalidApplicationContent_whenCreateApplication_thenReturnBadRequest() throws Exception {
            ApplicationCreateRequest applicationCreateRequest = applicationCreateRequestFactory.create(builder -> {
                builder.applicationContent(null);
            });

            ValidationException expectedValidationException = ValidationExceptionBuilder
                    .create()
                    .errors(List.of("Application content cannot be empty"))
                    .build();

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, applicationCreateRequest);
            ValidationException validationException = deserialise(result, ValidationException.class);

            // then
            assertSecurityHeaders(result);
            assertValidationException(HttpStatus.BAD_REQUEST, expectedValidationException.errors(), result, validationException);
            assertEquals(0, applicationRepository.count());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "{}"})
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenNoRequestBody_whenCreateApplication_thenReturnBadRequest(String request) throws Exception {
            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);
            ProblemDetail detail = deserialise(result, ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            assertProblemRecord(HttpStatus.BAD_REQUEST, "Bad Request", "Failed to read request", result, detail);
            assertEquals(0, applicationRepository.count());
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void givenCorrectRequestBodyAndReaderRole_whenCreateApplication_thenReturnForbidden() throws Exception {
            // given
            ApplicationCreateRequest request = applicationCreateRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }

        @Test
        public void givenCorrectRequestBodyAndNoAuthentication_whenCreateApplication_thenReturnUnauthorised() throws Exception {
            // given
            ApplicationCreateRequest request = applicationCreateRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);

            // then
            assertSecurityHeaders(result);
            assertUnauthorised(result);
        }

        private Stream<Arguments> applicationCreateRequestInvalidDataCases() {
            return Stream.of(
                    Arguments.of(applicationCreateRequestFactory.create(builder -> {
                                builder.status(null);
                            }), ProblemDetailBuilder
                                    .create()
                                    .status(HttpStatus.BAD_REQUEST)
                                    .title("Bad Request")
                                    .detail("Invalid request content.")
                                    .build()
                    ),
                    Arguments.of(applicationCreateRequestFactory.create(builder -> {
                                builder.laaReference(null);
                            }), ProblemDetailBuilder
                                    .create()
                                    .status(HttpStatus.BAD_REQUEST)
                                    .title("Bad Request")
                                    .detail("Invalid request content.")
                                    .build()
                    )
            );
        }

        private void assertApplicationEqual(ApplicationCreateRequest expected, ApplicationEntity actual) {
            assertNotNull(actual.getId());
            assertEquals(expected.getLaaReference(), actual.getLaaReference());
            assertEquals(expected.getApplicationContent(), actual.getApplicationContent());
            assertEquals(expected.getStatus(), actual.getStatus());
            assertEquals(applicationVersion, actual.getSchemaVersion());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class UpdateApplication {

        @Test
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenUpdateRequestWithNewContentAndStatus_whenUpdateApplication_thenReturnOK_andUpdateApplication() throws Exception {
            // given
            ApplicationEntity applicationEntity = persistedApplicationFactory.createAndPersist(builder -> {
                builder.applicationContent(new HashMap<>(Map.of(
                        "test", "content"
                )));
            });

            Map<String, Object> expectedContent = new HashMap<>(Map.of(
                    "test", "changed"
            ));

            ApplicationUpdateRequest applicationUpdateRequest = applicationUpdateRequestFactory.create(builder -> {
                builder.applicationContent(expectedContent).status(ApplicationStatus.SUBMITTED);
            });

            // when
            MvcResult result = patchUri(TestConstants.URIs.UPDATE_APPLICATION, applicationUpdateRequest, applicationEntity.getId());

            // then
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertNoContent(result);

            ApplicationEntity actual = applicationRepository.findById(applicationEntity.getId()).orElseThrow();
            assertThat(expectedContent)
                    .usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .isEqualTo(actual.getApplicationContent());
            assertEquals(ApplicationStatus.SUBMITTED, actual.getStatus());
        }

        @ParameterizedTest
        @MethodSource("invalidApplicationUpdateRequestCases")
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenUpdateRequestWithInvalidContent_whenUpdateApplication_thenReturnBadRequest(
                ApplicationUpdateRequest applicationUpdateRequest
        ) throws Exception {
            // given
            ApplicationEntity applicationEntity = persistedApplicationFactory.createAndPersist(builder -> {
                builder.applicationContent(new HashMap<>(Map.of(
                        "test", "content"
                )));
            });

            // when
            MvcResult result = patchUri(TestConstants.URIs.UPDATE_APPLICATION, applicationUpdateRequest, applicationEntity.getId());

            // then
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertBadRequest(result);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenUpdateRequestWithWrongId_whenUpdateApplication_thenReturnNotFound() throws Exception {
            // given
            ApplicationEntity applicationEntity = persistedApplicationFactory.createAndPersist(builder -> {
                builder.applicationContent(new HashMap<>(Map.of(
                        "test", "content"
                )));
            });

            ApplicationUpdateRequest applicationUpdateRequest = applicationUpdateRequestFactory.create();

            // when
            MvcResult result = patchUri(TestConstants.URIs.UPDATE_APPLICATION, applicationUpdateRequest, UUID.randomUUID());

            // then
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertNotFound(result);
        }

        @ParameterizedTest
        @ValueSource(strings = {"f8c3de3d-1fea-4d7c-a8b0", "not a UUID"})
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenUpdateRequestWithInvalidId_whenUpdateApplication_thenReturnNotFound(String uuid) throws Exception {
            // given
            persistedApplicationFactory.createAndPersist(builder -> {
                builder.applicationContent(new HashMap<>(Map.of(
                        "test", "content"
                )));
            });

            ApplicationUpdateRequest applicationUpdateRequest = applicationUpdateRequestFactory.create();

            // when
            MvcResult result = patchUri(TestConstants.URIs.UPDATE_APPLICATION, applicationUpdateRequest, uuid);

            // then
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertBadRequest(result);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void givenReaderRole_whenUpdateApplication_thenReturnForbidden() throws Exception {
            // given
            ApplicationUpdateRequest applicationUpdateRequest = applicationUpdateRequestFactory.create();

            // when
            MvcResult result = patchUri(TestConstants.URIs.UPDATE_APPLICATION, applicationUpdateRequest, UUID.randomUUID().toString());

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
        public void givenUnknownRole_whenUpdateApplication_thenReturnForbidden() throws Exception {
            // given
            ApplicationUpdateRequest applicationUpdateRequest = applicationUpdateRequestFactory.create();

            // when
            MvcResult result = patchUri(TestConstants.URIs.UPDATE_APPLICATION, applicationUpdateRequest, UUID.randomUUID().toString());

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }

        @Test
        public void givenNoUser_whenUpdateApplication_thenReturnUnauthorised() throws Exception {
            // given
            ApplicationUpdateRequest applicationUpdateRequest = applicationUpdateRequestFactory.create();

            // when
            MvcResult result = patchUri(TestConstants.URIs.UPDATE_APPLICATION, applicationUpdateRequest, UUID.randomUUID().toString());

            // then
            assertSecurityHeaders(result);
            assertUnauthorised(result);
        }

        private Stream<Arguments> invalidApplicationUpdateRequestCases() {
            return Stream.of(
                    Arguments.of(applicationUpdateRequestFactory.create(builder ->
                            builder.applicationContent(null))),
                    null
            );
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class AssignCaseworker {

        @ParameterizedTest
        @MethodSource("validAssignCaseworkerRequestCases")
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenValidAssignRequest_whenAssignCaseworker_thenReturnOK_andAssignCaseworker(
                AssignCaseworkerCase assignCaseworkerCase
        ) throws Exception {
            // given
            List<ApplicationEntity> expectedAssignedApplications = persistedApplicationFactory.createAndPersistMultiple(
                    assignCaseworkerCase.numberOfApplicationsToAssign,
                    builder -> {
                        builder.caseworker(null);
                    });

            List<ApplicationEntity> expectedAlreadyAssignedApplications = persistedApplicationFactory.createAndPersistMultiple(
                    assignCaseworkerCase.numberOfApplicationsAlreadyAssigned,
                    builder -> {
                        builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe);
                    });

            List<ApplicationEntity> expectedUnassignedApplications = persistedApplicationFactory.createAndPersistMultiple(
                    assignCaseworkerCase.numberOfApplicationsNotAssigned,
                    builder -> {
                        builder.caseworker(null);
                    });

            CaseworkerAssignRequest caseworkerAssignRequest = caseworkerAssignRequestFactory.create(builder -> {
                builder.caseworkerId(BaseIntegrationTest.CaseworkerJohnDoe.getId())
                        .applicationIds(expectedAssignedApplications.stream().map(ApplicationEntity::getId).collect(Collectors.toList()).reversed())
                        .eventHistory(EventHistory.builder()
                                .eventDescription("Assigning caseworker")
                                .build());
            });

            // when
            MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

            // then
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);

            assertApplicationsMatchInRepository(expectedAssignedApplications);
            assertApplicationsMatchInRepository(expectedAlreadyAssignedApplications);
            assertApplicationsMatchInRepository(expectedUnassignedApplications);
            assertDomainEventsCreatedForApplications(
                    expectedAssignedApplications,
                    BaseIntegrationTest.CaseworkerJohnDoe.getId(),
                    DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER,
                    caseworkerAssignRequest.getEventHistory()
            );
        }

        @ParameterizedTest
        @MethodSource("invalidAssignCaseworkerRequestCases")
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenInvalidAssignRequestBecauseApplicationDoesNotExist_whenAssignCaseworker_thenReturnNotFound_andGiveMissingIds(
                AssignCaseworkerCase assignCaseworkerCase
        ) throws Exception {
            // given
            List<ApplicationEntity> expectedAlreadyAssignedApplications = persistedApplicationFactory.createAndPersistMultiple(
                    assignCaseworkerCase.numberOfApplicationsAlreadyAssigned,
                    builder -> {
                        builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe);
                    });

            List<ApplicationEntity> expectedUnassignedApplications = persistedApplicationFactory.createAndPersistMultiple(
                    assignCaseworkerCase.numberOfApplicationsNotAssigned,
                    builder -> {
                        builder.caseworker(null);
                    });

            List<UUID> invalidApplicationIds = IntStream.range(0, assignCaseworkerCase.numberOfApplicationsToAssign)
                    .mapToObj(i -> UUID.randomUUID())
                    .toList();

            // generate random UUIDs so simulate records that do not exist..
            CaseworkerAssignRequest caseworkerAssignRequest = caseworkerAssignRequestFactory.create(builder -> {
                ;
                builder.caseworkerId(BaseIntegrationTest.CaseworkerJohnDoe.getId())
                        .applicationIds(invalidApplicationIds);
            });

            // when
            MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

            // then
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertNotFound(result);

            ProblemDetail problemResult = deserialise(result, ProblemDetail.class);
            // Extract UUIDs from the detail string
            Pattern uuidPattern = Pattern.compile("[0-9a-fA-F\\-]{36}");
            Set<UUID> actualIds = uuidPattern.matcher(problemResult.getDetail())
                    .results()
                    .map(MatchResult::group)
                    .map(UUID::fromString)
                    .collect(Collectors.toSet());

            assertEquals(new HashSet<>(invalidApplicationIds), actualIds);

            assertApplicationsMatchInRepository(expectedAlreadyAssignedApplications);
            assertApplicationsMatchInRepository(expectedUnassignedApplications);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenInvalidAssignRequestBecauseSomeApplicationsDoNotExist_whenAssignCaseworker_thenReturnNotFound_andAssignAvailableApplications_andGiveMissingIds() throws Exception {
            // given
            List<ApplicationEntity> expectedAssignedApplications = persistedApplicationFactory.createAndPersistMultiple(
                    4,
                    builder -> {
                        builder.caseworker(null);
                    });

            List<UUID> invalidApplicationIds = IntStream.range(0, 5)
                    .mapToObj(i -> UUID.randomUUID())
                    .toList();

            List<UUID> allApplicationIds = Stream.of(
                            expectedAssignedApplications.stream().map(ApplicationEntity::getId),
                            invalidApplicationIds.stream()
                    )
                    .flatMap(s -> s)
                    .collect(Collectors.toList());

            // generate random UUIDs so simulate records that do not exist..
            CaseworkerAssignRequest caseworkerAssignRequest = caseworkerAssignRequestFactory.create(builder -> {
                ;
                builder.caseworkerId(BaseIntegrationTest.CaseworkerJohnDoe.getId())
                        .applicationIds(allApplicationIds);
            });

            // when
            MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

            // then
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertNotFound(result);

            ProblemDetail problemResult = deserialise(result, ProblemDetail.class);
            // Extract UUIDs from the detail string
            Pattern uuidPattern = Pattern.compile("[0-9a-fA-F\\-]{36}");
            Set<UUID> actualIds = uuidPattern.matcher(problemResult.getDetail())
                    .results()
                    .map(MatchResult::group)
                    .map(UUID::fromString)
                    .collect(Collectors.toSet());

            assertEquals(new HashSet<>(invalidApplicationIds), actualIds);

            assertApplicationsMatchInRepository(expectedAssignedApplications);
        }

        @ParameterizedTest
        @MethodSource("invalidApplicationIdListsCases")
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenInvalidAssignmentRequestBecauseInvalidApplicationIds_whenAssignCaseworker_thenReturnBadRequest(
                List<UUID> invalidApplicationIdList
        ) throws Exception {
            // given
            ApplicationEntity expectedApplication = persistedApplicationFactory.createAndPersist(builder -> {
                builder.caseworker(null);
            });

            CaseworkerAssignRequest caseworkerAssignRequest = caseworkerAssignRequestFactory.create(builder -> {
                builder.caseworkerId(BaseIntegrationTest.CaseworkerJohnDoe.getId());
                builder.applicationIds(invalidApplicationIdList);
            });

            // when
            MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

            // then
            assertSecurityHeaders(result);
            assertBadRequest(result);

            ApplicationEntity actualApplication = applicationRepository.findById(expectedApplication.getId()).orElseThrow();
            assertNull(actualApplication.getCaseworker());
            assertEquals(expectedApplication, actualApplication);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenInvalidAssignmentRequestBecauseCaseworkerDoesNotExist_whenAssignCaseworker_thenReturnBadRequest() throws Exception {
            // given
            ApplicationEntity expectedApplication = persistedApplicationFactory.createAndPersist(builder -> {
                builder.caseworker(null);
            });

            CaseworkerAssignRequest caseworkerAssignRequest = caseworkerAssignRequestFactory.create(builder -> {
                builder.caseworkerId(null);
                builder.applicationIds(List.of(expectedApplication.getId()));
            });

            // when
            MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

            // then
            assertSecurityHeaders(result);
            assertBadRequest(result);

            ApplicationEntity actualApplication = applicationRepository.findById(expectedApplication.getId()).orElseThrow();
            assertNull(actualApplication.getCaseworker());
            assertEquals(expectedApplication, actualApplication);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void givenReaderRole_whenAssignCaseworker_thenReturnForbidden() throws Exception {
            // given
            CaseworkerAssignRequest caseworkerAssignRequest = caseworkerAssignRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
        public void givenUnknownRole_whenAssignCaseworker_thenReturnForbidden() throws Exception {
            // given
            CaseworkerAssignRequest caseworkerAssignRequest = caseworkerAssignRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }

        @Test
        public void givenNoUser_whenAssignCaseworker_thenReturnUnauthorised() throws Exception {
            // given
            CaseworkerAssignRequest caseworkerAssignRequest = caseworkerAssignRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

            // then
            assertSecurityHeaders(result);
            assertUnauthorised(result);
        }

        @Getter
        private class AssignCaseworkerCase {
            Integer numberOfApplicationsToAssign;
            Integer numberOfApplicationsAlreadyAssigned;
            Integer numberOfApplicationsNotAssigned;

            public AssignCaseworkerCase(
                    Integer numberOfApplicationsToAssign,
                    Integer numberOfApplicationsAlreadyAssigned,
                    Integer numberOfApplicationsNotAssigned) {
                this.numberOfApplicationsToAssign = numberOfApplicationsToAssign;
                this.numberOfApplicationsAlreadyAssigned = numberOfApplicationsAlreadyAssigned;
                this.numberOfApplicationsNotAssigned = numberOfApplicationsNotAssigned;
            }
        }

        private Stream<Arguments> validAssignCaseworkerRequestCases() {
            return Stream.of(
                    Arguments.of(new AssignCaseworkerCase(3, 3, 2)),
                    Arguments.of(new AssignCaseworkerCase(5, 0, 4)),
                    Arguments.of(new AssignCaseworkerCase(2, 4, 0))
            );
        }

        private Stream<Arguments> invalidAssignCaseworkerRequestCases() {
            return Stream.of(
                    Arguments.of(new AssignCaseworkerCase(5, 3, 2)),
                    Arguments.of(new AssignCaseworkerCase(2, 0, 4)),
                    Arguments.of(new AssignCaseworkerCase(4, 4, 0))
            );
        }

        private Stream<Arguments> invalidApplicationIdListsCases() {
            return Stream.of(
                    Arguments.of(Collections.emptyList()),
                    Arguments.of((Object)null)
            );
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class UnassignCaseworker {

        @Test
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenValidUnassignRequest_whenUnassignCaseworker_thenReturnOK_andUnassignCaseworker() throws Exception {
            // given
            ApplicationEntity expectedUnassignedApplication = persistedApplicationFactory.createAndPersist(builder -> {
                builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe);
            });

            CaseworkerUnassignRequest caseworkerUnassignRequest = caseworkerUnassignRequestFactory.create(builder -> {
                builder.eventHistory(EventHistory.builder()
                        .eventDescription("Unassigned Caseworker")
                        .build());
            });

            // when
            MvcResult result = postUri(TestConstants.URIs.UNASSIGN_CASEWORKER, caseworkerUnassignRequest, expectedUnassignedApplication.getId());

            // then
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);

            ApplicationEntity actual = applicationRepository.findById(expectedUnassignedApplication.getId()).orElseThrow();
            assertNull(actual.getCaseworker());
            assertEquals(expectedUnassignedApplication, actual);

            // TODO: verify domain event created when unassign domain event implemented
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenApplicationNotExist_whenUnassignCaseworker_thenReturnNotFound() throws Exception {
            // given
            CaseworkerUnassignRequest caseworkerUnassignRequest = caseworkerUnassignRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.UNASSIGN_CASEWORKER, caseworkerUnassignRequest, UUID.randomUUID());

            // then
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertNotFound(result);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenCaseworkerNotExist_whenUnassignCaseworker_thenReturnOK() throws Exception {
            // given
            ApplicationEntity expectedUnassignedApplication = persistedApplicationFactory.createAndPersist(builder -> {
                builder.caseworker(null);
            });

            CaseworkerUnassignRequest caseworkerUnassignRequest = caseworkerUnassignRequestFactory.create(builder -> {
                ;
                builder.eventHistory(EventHistory.builder()
                        .eventDescription("Unassigned Caseworker")
                        .build());
            });

            // when
            MvcResult result = postUri(TestConstants.URIs.UNASSIGN_CASEWORKER, caseworkerUnassignRequest, expectedUnassignedApplication.getId());

            // then
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            // TODO: is this right?
            assertOK(result);

            List<DomainEventEntity> domainEventEntities = domainEventRepository.findAll();
            assertEquals(0, domainEventEntities.size());
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void givenReaderRole_whenUnassignCaseworker_thenReturnForbidden() throws Exception {
            // given
            CaseworkerUnassignRequest caseworkerUnassignRequest = caseworkerUnassignRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.UNASSIGN_CASEWORKER, caseworkerUnassignRequest, UUID.randomUUID().toString());

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
        public void givenUnknownRole_whenUnassignCaseworker_thenReturnForbidden() throws Exception {
            // given
            CaseworkerUnassignRequest caseworkerUnassignRequest = caseworkerUnassignRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.UNASSIGN_CASEWORKER, caseworkerUnassignRequest, UUID.randomUUID().toString());

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }

        @Test
        public void givenNoUser_whenUnassignCaseworker_thenReturnUnauthorised() throws Exception {
            // given
            CaseworkerUnassignRequest caseworkerUnassignRequest = caseworkerUnassignRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.UNASSIGN_CASEWORKER, caseworkerUnassignRequest, UUID.randomUUID().toString());

            // then
            assertSecurityHeaders(result);
            assertUnauthorised(result);
        }
    }

    // invalid reassign is covered by invalid assign tests
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ReassignCaseworker {

        @Test
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenValiReassignRequest_whenAssignCaseworker_thenReturnOK_andAssignCaseworker() throws Exception {
            // given
            List<ApplicationEntity> expectedReassignedApplications = persistedApplicationFactory.createAndPersistMultiple(
                    4,
                    builder -> {
                        builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe);
                    });

            List<ApplicationEntity> expectedAlreadyAssignedApplications = persistedApplicationFactory.createAndPersistMultiple(
                    5,
                    builder -> {
                        builder.caseworker(BaseIntegrationTest.CaseworkerJaneDoe);
                    });

            List<ApplicationEntity> expectedUnassignedApplications = persistedApplicationFactory.createAndPersistMultiple(
                    8,
                    builder -> {
                        builder.caseworker(null);
                    });

            CaseworkerAssignRequest caseworkerReassignRequest = caseworkerAssignRequestFactory.create(builder -> {
                ;
                builder.caseworkerId(BaseIntegrationTest.CaseworkerJaneDoe.getId())
                        .applicationIds(expectedReassignedApplications.stream().map(ApplicationEntity::getId).collect(Collectors.toList()))
                        .eventHistory(EventHistory.builder()
                                .eventDescription("Assigning caseworker")
                                .build());
            });

            // when
            MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerReassignRequest);

            // then
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);

            assertApplicationsMatchInRepository(expectedReassignedApplications);
            assertApplicationsMatchInRepository(expectedAlreadyAssignedApplications);
            assertApplicationsMatchInRepository(expectedUnassignedApplications);
            assertDomainEventsCreatedForApplications(
                    expectedReassignedApplications,
                    BaseIntegrationTest.CaseworkerJaneDoe.getId(),
                    DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER,
                    caseworkerReassignRequest.getEventHistory()
            );
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class GetApplications {

        public static final String SEARCH_PAGE_PARAM = "page=";
        public static final String SEARCH_PAGE_SIZE_PARAM = "pagesize=";
        public static final String SEARCH_STATUS_PARAM = "status=";
        public static final String SEARCH_FIRSTNAME_PARAM = "firstname=";
        public static final String SEARCH_LASTNAME_PARAM = "lastname=";
        public static final String SEARCH_CASEWORKERID_PARAM = "userid=";

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsWithoutFiltering_whenGetApplications_thenReturnApplicationsWithPagingCorrectly() throws Exception {
            // given
            List<ApplicationEntity> expectedApplicationsWithCaseworker = persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                    builder.status(ApplicationStatus.IN_PROGRESS));
            List<ApplicationEntity> expectedApplicationWithDifferentCaseworker = persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                    builder.status(ApplicationStatus.IN_PROGRESS).caseworker(CaseworkerJaneDoe));
            List<ApplicationEntity> expectedApplicationWithNoCaseworker = persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                    builder.status(ApplicationStatus.IN_PROGRESS).caseworker(null));

            List<ApplicationSummary> expectedApplicationsSummary = Stream.of(
                            expectedApplicationsWithCaseworker,
                            expectedApplicationWithDifferentCaseworker,
                            expectedApplicationWithNoCaseworker
                    )
                    .flatMap(List::stream)
                    .map(this::createApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 9, 10, 1, 9);
            assertThat(actual.getApplications().size()).isEqualTo(9);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsRequiringPageTwo_whenGetApplications_thenReturnSecondPageOfApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory.createAndPersistMultiple(20, builder ->
                            builder.status(ApplicationStatus.IN_PROGRESS))
                    .stream()
                    .map(this::createApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_PAGE_PARAM + "2");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 20, 10, 2, 10);
            assertThat(actual.getApplications().size()).isEqualTo(10);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(10, 20)));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsAndPageSizeOfTwenty_whenGetApplications_thenReturnTwentyRecords() throws Exception {
            // given
            List<ApplicationEntity> inProgressApplications = persistedApplicationFactory.createAndPersistMultiple(15, builder -> builder.status(ApplicationStatus.IN_PROGRESS));
            List<ApplicationEntity> submittedApplications = persistedApplicationFactory.createAndPersistMultiple(10, builder -> builder.status(ApplicationStatus.SUBMITTED));

            List<ApplicationSummary> expectedApplicationsSummary = Stream.concat(
                            inProgressApplications.stream(),
                            submittedApplications.stream().limit(5)
                    )
                    .map(this::createApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_PAGE_SIZE_PARAM + "20");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 25, 20, 1, 20);
            assertThat(actual.getApplications().size()).isEqualTo(20);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
        }

        @ParameterizedTest
        @MethodSource("applicationsSummaryFilteredByStatusCases")
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly(
                ApplicationStatus applicationStatus,
                Supplier<List<ApplicationSummary>> expectedApplicationsSummarySupplier,
                int numberOfApplications
        ) throws Exception {
            // given
            List<ApplicationSummary> expectedApplicationsSummary = expectedApplicationsSummarySupplier.get();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_STATUS_PARAM + applicationStatus);
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, numberOfApplications, 10, 1, numberOfApplications);
            assertThat(actual.getApplications().size()).isEqualTo(numberOfApplications);
            assertTrue((actual.getApplications()).containsAll(expectedApplicationsSummary));
        }

        // TODO: is this test superseded by parameterized test above?
        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByInProgressStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory
                    .createAndPersistMultiple(5, builder -> builder.status(ApplicationStatus.IN_PROGRESS))
                    .stream()
                    .map(this::createApplicationSummary)
                    .toList();

            persistedApplicationFactory.createAndPersistMultiple(10, builder -> builder.status(ApplicationStatus.SUBMITTED));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.IN_PROGRESS);
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 5, 10, 1, 5);
            assertThat(actual.getApplications().size()).isEqualTo(5);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
        }

        // TODO: is this test superseded by parameterized test above?
        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredBySubmittedStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory
                    .createAndPersistMultiple(6, builder -> builder.status(ApplicationStatus.SUBMITTED))
                    .stream()
                    .map(this::createApplicationSummary)
                    .toList();
            persistedApplicationFactory.createAndPersistMultiple(10, builder -> builder.status(ApplicationStatus.IN_PROGRESS));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.SUBMITTED);
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 6, 10, 1, 6);
            assertThat(actual.getApplications().size()).isEqualTo(6);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredBySubmittedStatusWithPaging_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory
                    .createAndPersistMultiple(17, builder -> builder.status(ApplicationStatus.SUBMITTED))
                    .stream()
                    .map(this::createApplicationSummary)
                    .toList();
            persistedApplicationFactory.createAndPersistMultiple(10, builder -> builder.status(ApplicationStatus.IN_PROGRESS));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                    + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.SUBMITTED
                    + "&" + SEARCH_PAGE_PARAM + "2");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 17, 10, 2, 7);
            assertThat(actual.getApplications().size()).isEqualTo(7);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(10, 17)));
        }

        @ParameterizedTest
        @MethodSource("firstNameSearchCases")
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByFirstName_whenGetApplications_thenReturnExpectedApplicationsCorrectly(
                String searchFirstName,
                String persistedFirstName,
                int expectedCount
        ) throws Exception {
            // given
            persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                    builder.individuals(Set.of(individualFactory.create(i -> i.firstName("John")))));

            List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory.createAndPersistMultiple(expectedCount, builder ->
                            builder.individuals(Set.of(individualFactory.create(i -> i.firstName(persistedFirstName)))))
                    .stream()
                    .map(this::createApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_FIRSTNAME_PARAM + searchFirstName);
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, expectedCount, 10, 1, expectedCount);
            assertThat(actual.getApplications().size()).isEqualTo(expectedCount);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByFirstNameAndStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            persistedApplicationFactory.createAndPersistMultiple(8, builder ->
                    builder.status(ApplicationStatus.IN_PROGRESS)
                            .individuals(Set.of(individualFactory.create(i -> i.firstName("Jane")))));

            List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory.createAndPersistMultiple(7, builder ->
                            builder.status(ApplicationStatus.SUBMITTED)
                                    .individuals(Set.of(individualFactory.create(i -> i.firstName("Jane")))))
                    .stream()
                    .map(this::createApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                    + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.SUBMITTED
                    + "&" + SEARCH_FIRSTNAME_PARAM + "Jane");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 7, 10, 1, 7);
            assertThat(actual.getApplications().size()).isEqualTo(7);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
        }

        @ParameterizedTest
        @MethodSource("lastNameSearchCases")
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByLastName_whenGetApplications_thenReturnExpectedApplicationsCorrectly(
                String searchLastName,
                String persistedLastName,
                int expectedCount
        ) throws Exception {
            // given
            persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                    builder.individuals(Set.of(individualFactory.create(i -> i.lastName("Johnson")))));
            List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory.createAndPersistMultiple(expectedCount, builder ->
                            builder.individuals(Set.of(individualFactory.create(i -> i.lastName(persistedLastName)))))
                    .stream()
                    .map(this::createApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_LASTNAME_PARAM + searchLastName);
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, expectedCount, 10, 1, expectedCount);
            assertThat(actual.getApplications().size()).isEqualTo(expectedCount);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByLastNameAndStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            persistedApplicationFactory.createAndPersistMultiple(1, builder ->
                    builder.status(ApplicationStatus.IN_PROGRESS)
                            .individuals(Set.of(individualFactory.create(i -> i.lastName("David")))));

            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(7, builder ->
                    builder.status(ApplicationStatus.SUBMITTED)
                            .individuals(Set.of(individualFactory.create(i -> i.lastName("David")))));

            persistedApplicationFactory.createAndPersistMultiple(5, builder ->
                    builder.status(ApplicationStatus.IN_PROGRESS)
                            .individuals(Set.of(individualFactory.create(i -> i.lastName("Smith")))));

            List<ApplicationSummary> expectedApplicationsSummary = expectedApplications.stream()
                    .map(this::createApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                    + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.SUBMITTED
                    + "&" + SEARCH_LASTNAME_PARAM + "David");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 7, 10, 1, 7);
            assertThat(actual.getApplications().size()).isEqualTo(7);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByFirstNameAndLastName_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                    builder.individuals(Set.of(individualFactory.create(i -> i.firstName("George").lastName("Taylor")))));
            List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory.createAndPersistMultiple(2, builder ->
                            builder.individuals(Set.of(individualFactory.create(i -> i.firstName("Lucas").lastName("Taylor")))))
                    .stream()
                    .map(this::createApplicationSummary)
                    .toList();
            persistedApplicationFactory.createAndPersistMultiple(5, builder ->
                    builder.individuals(Set.of(individualFactory.create(i -> i.firstName("Victoria").lastName("Williams")))));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                    + "?" + SEARCH_FIRSTNAME_PARAM + "Lucas"
                    + "&" + SEARCH_LASTNAME_PARAM + "Taylor");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 2, 10, 1, 2);
            assertThat(actual.getApplications().size()).isEqualTo(2);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByFirstNameAndLastNameAndStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(1, builder ->
                    builder.status(ApplicationStatus.IN_PROGRESS)
                            .individuals(Set.of(individualFactory.create(i -> i.firstName("George").lastName("Theodore")))));

            persistedApplicationFactory.createAndPersistMultiple(3, builder -> builder.status(ApplicationStatus.SUBMITTED)
                    .individuals(Set.of(individualFactory.create(i -> i.firstName("George").lastName("Theodore")))));
            persistedApplicationFactory.createAndPersistMultiple(2, builder -> builder.individuals(Set.of(individualFactory.create(i -> i.firstName("Lucas").lastName("Jones")))));
            persistedApplicationFactory.createAndPersistMultiple(5, builder -> builder.individuals(Set.of(individualFactory.create(i -> i.firstName("Victoria").lastName("Theodore")))));

            List<ApplicationSummary> expectedApplicationsSummary = expectedApplications.stream()
                    .map(this::createApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                    + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.IN_PROGRESS
                    + "&" + SEARCH_FIRSTNAME_PARAM + "George"
                    + "&" + SEARCH_LASTNAME_PARAM + "Theodore");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 1, 10, 1, 1);
            assertThat(actual.getApplications().size()).isEqualTo(1);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByFirstNameAndLastNameAndStatusWithPaging_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(13, builder ->
                    builder.status(ApplicationStatus.IN_PROGRESS)
                            .individuals(Set.of(individualFactory.create(i -> i.firstName("George").lastName("Theodore")))));

            persistedApplicationFactory.createAndPersistMultiple(3, builder -> builder.status(ApplicationStatus.SUBMITTED)
                    .individuals(Set.of(individualFactory.create(i -> i.firstName("George").lastName("Theodore")))));
            persistedApplicationFactory.createAndPersistMultiple(2, builder -> builder.individuals(Set.of(individualFactory.create(i -> i.firstName("Lucas").lastName("Jones")))));
            persistedApplicationFactory.createAndPersistMultiple(5, builder -> builder.individuals(Set.of(individualFactory.create(i -> i.firstName("Victoria").lastName("Theodore")))));

            List<ApplicationSummary> expectedApplicationsSummary = expectedApplications.stream()
                    .map(this::createApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                    + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.IN_PROGRESS
                    + "&" + SEARCH_FIRSTNAME_PARAM + "George"
                    + "&" + SEARCH_LASTNAME_PARAM + "Theodore"
                    + "&" + SEARCH_PAGE_PARAM + "2");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 13, 10, 2, 3);
            assertThat(actual.getApplications().size()).isEqualTo(3);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(10, 13)));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByStatusAndNoApplicationsMatch_whenGetApplications_thenReturnEmptyResult() throws Exception {
            // given
            persistedApplicationFactory.createAndPersistMultiple(7, builder ->
                    builder
                            .status(ApplicationStatus.IN_PROGRESS)
                            .individuals(Set.of(individualFactory.create(i -> i.firstName("George").lastName("Theodore")))));
            persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                    builder
                            .status(ApplicationStatus.SUBMITTED)
                            .individuals(Set.of(individualFactory.create(i -> i.firstName("George").lastName("Theodore")))));
            persistedApplicationFactory.createAndPersistMultiple(2, builder ->
                    builder
                            .status(ApplicationStatus.SUBMITTED)
                            .individuals(Set.of(individualFactory.create(i -> i.firstName("Lucas").lastName("Jones")))));
            persistedApplicationFactory.createAndPersistMultiple(5, builder ->
                    builder.individuals(Set.of(individualFactory.create(i -> i.firstName("Victoria").lastName("Theodore")))));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                    + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.IN_PROGRESS
                    + "&" + SEARCH_FIRSTNAME_PARAM + "Lucas"
                    + "&" + SEARCH_LASTNAME_PARAM + "Jones");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 0, 10, 1, 0);
            assertThat(actual.getApplications().size()).isEqualTo(0);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void givenApplicationsFilteredByCaseworkerJohnDoe_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(4, builder ->
                    builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe));

            persistedApplicationFactory.createAndPersistMultiple(6, builder ->
                    builder.caseworker(BaseIntegrationTest.CaseworkerJaneDoe));

            List<ApplicationSummary> expectedApplicationsSummary = expectedApplications.stream()
                    .map(this::createApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_CASEWORKERID_PARAM + BaseIntegrationTest.CaseworkerJohnDoe.getId());
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 4, 10, 1, 4);
            assertThat(actual.getApplications().size()).isEqualTo(4);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void givenPageZero_whenGetApplications_thenDefaultToPageOneAndReturnCorrectResults() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(15, builder ->
                    builder.status(ApplicationStatus.IN_PROGRESS));

            List<ApplicationSummary> expectedApplicationsSummary = expectedApplications.stream()
                    .map(this::createApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_PAGE_PARAM + "0");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 15, 10, 1, 10);
            assertThat(actual.getApplications().size()).isEqualTo(10);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(0, 10)));
        }

        @Test
        public void givenNoUser_whenGetApplications_thenReturnUnauthorised() throws Exception {
            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);

            // then
            assertSecurityHeaders(result);
            assertUnauthorised(result);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
        public void givenNoRole_whenGetApplications_thenReturnForbidden() throws Exception {
            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }

        private void assertPaging(
                ApplicationSummaryResponse applicationSummaryResponse,
                Integer totalRecords,
                Integer pageSize,
                Integer page,
                Integer itemsReturned
        ) {
            assertThat(applicationSummaryResponse.getPaging().getTotalRecords()).isEqualTo(totalRecords);
            assertThat(applicationSummaryResponse.getPaging().getPageSize()).isEqualTo(pageSize);
            assertThat(applicationSummaryResponse.getPaging().getPage()).isEqualTo(page);
            assertThat(applicationSummaryResponse.getPaging().getItemsReturned()).isEqualTo(itemsReturned);
        }

        private static Stream<Arguments> firstNameSearchCases() {
            return Stream.of(
                    Arguments.of("Jane", "Jane", 5),
                    Arguments.of("Jan", "Jane", 3),
                    Arguments.of("ne", "Jane", 7),
                    Arguments.of("an", "Jane", 4),
                    Arguments.of("ANE", "Jane", 6)
            );
        }

        private static Stream<Arguments> lastNameSearchCases() {
            return Stream.of(
                    Arguments.of("Smith", "Smith", 3),
                    Arguments.of("Smi", "Smith", 7),
                    Arguments.of("ith", "Smith", 5),
                    Arguments.of("mit", "Smith", 8),
                    Arguments.of("MITH", "Smith", 4)
            );
        }

        private Stream<Arguments> applicationsSummaryFilteredByStatusCases() {
            return Stream.of(
                    Arguments.of(ApplicationStatus.IN_PROGRESS, (Supplier<List<ApplicationSummary>>) () -> generateApplicationSummaries(ApplicationStatus.IN_PROGRESS, 8), 8),
                    Arguments.of(ApplicationStatus.SUBMITTED, (Supplier<List<ApplicationSummary>>) () -> generateApplicationSummaries(ApplicationStatus.SUBMITTED, 5), 5)
            );
        }

        private List<ApplicationSummary> generateApplicationSummaries(ApplicationStatus status, int numberOfApplications) {
            Random random = new Random();

            return persistedApplicationFactory.createAndPersistMultiple(numberOfApplications, builder ->
                            builder.status(status).laaReference("REF-00" + random.nextInt(100)))
                    .stream()
                    .map(this::createApplicationSummary)
                    .collect(Collectors.toList());
        }

        private ApplicationSummary createApplicationSummary(ApplicationEntity applicationEntity) {
            ApplicationSummary applicationSummary = new ApplicationSummary();
            applicationSummary.setApplicationId(applicationEntity.getId());
            applicationSummary.setApplicationStatus(applicationEntity.getStatus());
            applicationSummary.setLaaReference(applicationEntity.getLaaReference());
            applicationSummary.setCreatedAt(OffsetDateTime.ofInstant(applicationEntity.getCreatedAt(), ZoneOffset.UTC));
            applicationSummary.setModifiedAt(OffsetDateTime.ofInstant(applicationEntity.getModifiedAt(), ZoneOffset.UTC));
            if (applicationEntity.getCaseworker() != null) {
                applicationSummary.setAssignedTo(applicationEntity.getCaseworker().getId());
            }
            return applicationSummary;
        }
    }

    // <editor-fold desc="Shared asserts">

    private void assertApplicationsMatchInRepository(List<ApplicationEntity> expected) {
        List<ApplicationEntity> actual = applicationRepository.findAllById(
                expected.stream().map(ApplicationEntity::getId).collect(Collectors.toList()));
        assertThat(expected.size()).isEqualTo(actual.size());
        assertTrue(expected.containsAll(actual));
    }

    private void assertDomainEventsCreatedForApplications(
            List<ApplicationEntity> applications,
            UUID caseWorkerId,
            DomainEventType expectedDomainEventType,
            EventHistory expectedEventHistory
    ) {

        List<DomainEventEntity> domainEvents = domainEventRepository.findAll();

        assertEquals(applications.size(), domainEvents.size());

        List<UUID> applicationIds = applications.stream()
                .map(ApplicationEntity::getId)
                .collect(Collectors.toList());

        for (DomainEventEntity domainEvent : domainEvents) {
            assertEquals(expectedDomainEventType, domainEvent.getType());
            assertTrue(applicationIds.contains(domainEvent.getApplicationId()));
            assertEquals(caseWorkerId, domainEvent.getCaseworkerId());
            // TODO: improve event data comparison
            assertTrue(domainEvent.getData().contains(expectedEventHistory.getEventDescription()));
        }
    }

    // </editor-fold>
}
package uk.gov.justice.laa.dstew.access.controller;

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
import java.util.stream.Collectors;
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
            Application expectedApplication = toApplication(application);

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
            var json = objectMapper.readTree(result.getResponse().getContentAsString());
            assertEquals("No application found with id: " + notExistApplicationId, json.get("detail").asText());

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

        private Application toApplication(ApplicationEntity applicationEntity) {
            Application application = new Application();
            application.setId(applicationEntity.getId());
            application.setApplicationContent(applicationEntity.getApplicationContent());
            application.setApplicationStatus(applicationEntity.getStatus());
            application.setSchemaVersion(applicationEntity.getSchemaVersion());
            if (applicationEntity.getCaseworker() != null) {
                application.setCaseworkerId(applicationEntity.getCaseworker().getId());
            }
            if(applicationEntity.getIndividuals() != null) {
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
            ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

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
            ValidationException validationException = objectMapper.readValue(result.getResponse().getContentAsString(), ValidationException.class);

            // then
            assertSecurityHeaders(result);
            assertValidationException(HttpStatus.BAD_REQUEST, expectedValidationException.errors(), result, validationException);
            assertEquals(0, applicationRepository.count());
        }

        @ParameterizedTest
        @ValueSource(strings = { "", "{}" })
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenNoRequestBody_whenCreateApplication_thenReturnBadRequest(String request) throws Exception {
            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);
            ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

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
                                builder.applicationReference(null);
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
            assertEquals(expected.getApplicationReference(), actual.getApplicationReference());
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
        @ValueSource(strings = { "f8c3de3d-1fea-4d7c-a8b0", "not a UUID" })
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

        @Test
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenValidAssignRequest_whenAssignCaseworker_thenReturnOK_andAssignCaseworker() throws Exception {
            // given
            ApplicationEntity expectedApplication = persistedApplicationFactory.createAndPersist(builder -> {
                builder.caseworker(null);
            });

            CaseworkerAssignRequest caseworkerAssignRequest = caseworkerAssignRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest, expectedApplication.getId());

            // then
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);

            ApplicationEntity actualApplication = applicationRepository.findById(expectedApplication.getId()).orElseThrow();
            assertEquals(caseworkerAssignRequest.getCaseworkerId(), actualApplication.getCaseworker().getId());
            assertEquals(expectedApplication, actualApplication);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenInvalidAssignmentRequest_whenAssignCaseworker_thenReturnBadRequest() throws Exception {
            // given
            ApplicationEntity expectedApplication = persistedApplicationFactory.createAndPersist(builder -> {
                builder.caseworker(null);
            });

            CaseworkerAssignRequest caseworkerAssignRequest = caseworkerAssignRequestFactory.create(builder -> {
                builder.caseworkerId(null);
            });

            // when
            MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest, expectedApplication.getId());

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
            MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest, UUID.randomUUID().toString());

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
            MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest, UUID.randomUUID().toString());

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }

        @Test
        public void givenNoUser_whenAssignCaseworker_thenReturnUnauthorised() throws Exception {
            // given
            CaseworkerAssignRequest caseworkerAssignRequest = caseworkerAssignRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest, UUID.randomUUID().toString());

            // then
            assertSecurityHeaders(result);
            assertUnauthorised(result);
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class UnassignCaseworker {

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void givenReaderRole_whenUnassignCaseworker_thenReturnForbidden() throws Exception {
            // when
            MvcResult result = postUriWithoutModel(TestConstants.URIs.UNASSIGN_CASEWORKER, UUID.randomUUID().toString());

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
        public void givenUnknownRole_whenUnassignCaseworker_thenReturnForbidden() throws Exception {
            // when
            MvcResult result = postUriWithoutModel(TestConstants.URIs.UNASSIGN_CASEWORKER, UUID.randomUUID().toString());

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }

        @Test
        public void givenNoUser_whenUnassignCaseworker_thenReturnUnauthorised() throws Exception {
            // when
            MvcResult result = postUriWithoutModel(TestConstants.URIs.UNASSIGN_CASEWORKER, UUID.randomUUID().toString());

            // then
            assertSecurityHeaders(result);
            assertUnauthorised(result);
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ReassignCaseworker {

    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class GetApplications {

        public static final String SEARCH_PAGE_PARAM = "page=";
        public static final String SEARCH_PAGE_SIZE_PARAM = "pagesize=";
        public static final String SEARCH_STATUS_PARAM = "status=";
        public static final String SEARCH_FIRSTNAME_PARAM = "firstname=";
        public static final String SEARCH_LASTNAME_PARAM = "lastname=";

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
                    .map(this::toApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 9, 10,0,9);
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
                    .map(this::toApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_PAGE_PARAM + "1");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 20, 10,1,10);
            assertThat(actual.getApplications().size()).isEqualTo(10);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(10,20)));
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
                    .map(this::toApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_PAGE_SIZE_PARAM + "20");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 25, 20,0,20);
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
            assertPaging(actual, numberOfApplications, 10,0,numberOfApplications);
            assertThat(actual.getApplications().size()).isEqualTo(numberOfApplications);
            assertTrue((actual.getApplications()).containsAll(expectedApplicationsSummary));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByInProgressStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory
                    .createAndPersistMultiple(5, builder -> builder.status(ApplicationStatus.IN_PROGRESS))
                    .stream()
                    .map(this::toApplicationSummary)
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
            assertPaging(actual, 5, 10,0,5);
            assertThat(actual.getApplications().size()).isEqualTo(5);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredBySubmittedStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory
                    .createAndPersistMultiple(6, builder -> builder.status(ApplicationStatus.SUBMITTED))
                    .stream()
                    .map(this::toApplicationSummary)
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
            assertPaging(actual, 6, 10,0,6);
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
                    .map(this::toApplicationSummary)
                    .toList();
            persistedApplicationFactory.createAndPersistMultiple(10, builder -> builder.status(ApplicationStatus.IN_PROGRESS));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                    + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.SUBMITTED
                    + "&" + SEARCH_PAGE_PARAM + "1");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 17, 10,1,7);
            assertThat(actual.getApplications().size()).isEqualTo(7);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(10,17)));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByFirstName_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                    builder.individuals(Set.of(individualFactory.create(i -> i.firstName("John")))));
            List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory.createAndPersistMultiple(5, builder ->
                            builder.individuals(Set.of(individualFactory.create(i -> i.firstName("Jane")))))
                    .stream()
                    .map(this::toApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_FIRSTNAME_PARAM + "Jane");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 5, 10,0,5);
            assertThat(actual.getApplications().size()).isEqualTo(5);
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
                    .map(this::toApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                    + "?"  + SEARCH_STATUS_PARAM + ApplicationStatus.SUBMITTED
                    + "&" + SEARCH_FIRSTNAME_PARAM + "Jane");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 7, 10,0,7);
            assertThat(actual.getApplications().size()).isEqualTo(7);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByLastName_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                    builder.individuals(Set.of(individualFactory.create(i -> i.lastName("David")))));
            persistedApplicationFactory.createAndPersistMultiple(5, builder ->
                    builder.individuals(Set.of(individualFactory.create(i -> i.lastName("Smith")))));
            List<ApplicationSummary> expectedApplicationsSummary = expectedApplications.stream()
                    .map(this::toApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_LASTNAME_PARAM + "David");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 3, 10,0,3);
            assertThat(actual.getApplications().size()).isEqualTo(3);
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
                    .map(this::toApplicationSummary)
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
            assertPaging(actual, 7, 10,0,7);
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
                    .map(this::toApplicationSummary)
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
            assertPaging(actual, 2, 10,0,2);
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
                    .map(this::toApplicationSummary)
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
            assertPaging(actual, 1, 10,0,1);
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
                    .map(this::toApplicationSummary)
                    .toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                    + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.IN_PROGRESS
                    + "&" + SEARCH_FIRSTNAME_PARAM + "George"
                    + "&" + SEARCH_LASTNAME_PARAM + "Theodore"
                    + "&" + SEARCH_PAGE_PARAM + "1");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 13, 10,1,3);
            assertThat(actual.getApplications().size()).isEqualTo(3);
            assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(10,13)));
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
            assertPaging(actual, 0, 10,0,0);
            assertThat(actual.getApplications().size()).isEqualTo(0);
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

        private Stream<Arguments> applicationsSummaryFilteredByStatusCases() {
            return Stream.of(
                    Arguments.of(ApplicationStatus.IN_PROGRESS, (Supplier<List<ApplicationSummary>>) () -> generateApplicationSummaries(ApplicationStatus.IN_PROGRESS, 8), 8),
                    Arguments.of(ApplicationStatus.SUBMITTED, (Supplier<List<ApplicationSummary>>) () -> generateApplicationSummaries(ApplicationStatus.SUBMITTED, 5), 5)
            );
        }

        private List<ApplicationSummary> generateApplicationSummaries(ApplicationStatus status, int numberOfApplications) {
            Random random = new Random();

            return persistedApplicationFactory.createAndPersistMultiple(numberOfApplications, builder ->
                            builder.status(status).applicationReference("REF-00" + random.nextInt(100)))
                    .stream()
                    .map(this::toApplicationSummary)
                    .collect(Collectors.toList());
        }

        private ApplicationSummary toApplicationSummary(ApplicationEntity applicationEntity) {
            ApplicationSummary applicationSummary = new ApplicationSummary();
            applicationSummary.setApplicationId(applicationEntity.getId());
            applicationSummary.setApplicationStatus(applicationEntity.getStatus());
            applicationSummary.setApplicationReference(applicationEntity.getApplicationReference());
            applicationSummary.setCreatedAt(OffsetDateTime.ofInstant(applicationEntity.getCreatedAt(), ZoneOffset.UTC));
            applicationSummary.setModifiedAt(OffsetDateTime.ofInstant(applicationEntity.getModifiedAt(), ZoneOffset.UTC));
            if (applicationEntity.getCaseworker() != null) {
                applicationSummary.setAssignedTo(applicationEntity.getCaseworker().getId());
            }
            return applicationSummary;
        }
    }
}
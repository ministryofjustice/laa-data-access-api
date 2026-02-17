package uk.gov.justice.laa.dstew.access.controller.application;

import lombok.Getter;
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
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.EventHistory;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerAssignRequestGenerator;

import java.util.UUID;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertBadRequest;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNotFound;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AssignCaseworkerTest extends BaseIntegrationTest {

    @ParameterizedTest
    @WithMockUser(authorities = TestConstants.Roles.READER)
    @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
    void givenValidAssignRequestAndInvalidHeader_whenAssignCaseworker_thenReturnBadRequest(
            String serviceName
    ) throws Exception {
        verifyBadServiceNameHeader(serviceName);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenValidAssignRequestAndBlankHeader_whenAssignCaseworker_thenReturnBadRequest() throws Exception {
        verifyBadServiceNameHeader("");
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenValidAssignRequestAndNoHeader_whenAssignCaseworker_thenReturnBadRequest() throws Exception {
        verifyBadServiceNameHeader(null);
    }

    private void verifyBadServiceNameHeader(String serviceName) throws Exception {

        CaseworkerAssignRequest caseworkerAssignRequest = DataGenerator.createDefault(CaseworkerAssignRequestGenerator.class, builder -> {
            builder.caseworkerId(BaseIntegrationTest.CaseworkerJohnDoe.getId())
                    .applicationIds(List.of(UUID.randomUUID()))
                    .eventHistory(EventHistory.builder()
                            .eventDescription("Assigning caseworker")
                            .build());
        });

        MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest, ServiceNameHeader(serviceName));
        applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
    }

    @ParameterizedTest
    @MethodSource("validAssignCaseworkerRequestCases")
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenValidAssignRequest_whenAssignCaseworker_thenReturnOK_andAssignCaseworker(
            AssignCaseworkerCase assignCaseworkerCase
    ) throws Exception {
        // given
        List<ApplicationEntity> toAssignApplications = persistedDataGenerator.createAndPersistMultiple(ApplicationEntityGenerator.class,
                assignCaseworkerCase.numberOfApplicationsToAssign,
                builder -> builder.caseworker(null));

        List<ApplicationEntity> expectedAssignedApplications = toAssignApplications.stream()
                .peek(application -> application.setCaseworker(BaseIntegrationTest.CaseworkerJohnDoe))
                .toList();

        List<ApplicationEntity> expectedAlreadyAssignedApplications = persistedDataGenerator.createAndPersistMultiple(ApplicationEntityGenerator.class,
                assignCaseworkerCase.numberOfApplicationsAlreadyAssigned,
                builder -> builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe));

        List<ApplicationEntity> expectedUnassignedApplications = persistedDataGenerator.createAndPersistMultiple(ApplicationEntityGenerator.class,
                assignCaseworkerCase.numberOfApplicationsNotAssigned,
                builder -> builder.caseworker(null));

        CaseworkerAssignRequest caseworkerAssignRequest = DataGenerator.createDefault(CaseworkerAssignRequestGenerator.class, builder -> {
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

        applicationAsserts.assertApplicationsMatchInRepository(expectedAssignedApplications);
        applicationAsserts.assertApplicationsMatchInRepository(expectedAlreadyAssignedApplications);
        applicationAsserts.assertApplicationsMatchInRepository(expectedUnassignedApplications);
        domainEventAsserts.assertDomainEventsCreatedForApplications(
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
        List<ApplicationEntity> expectedAlreadyAssignedApplications = persistedDataGenerator.createAndPersistMultiple(ApplicationEntityGenerator.class,
                assignCaseworkerCase.numberOfApplicationsAlreadyAssigned,
                builder -> builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe));

        List<ApplicationEntity> expectedUnassignedApplications = persistedDataGenerator.createAndPersistMultiple(ApplicationEntityGenerator.class,
                assignCaseworkerCase.numberOfApplicationsNotAssigned,
                builder -> builder.caseworker(null));

        List<UUID> invalidApplicationIds = IntStream.range(0, assignCaseworkerCase.numberOfApplicationsToAssign)
                .mapToObj(i -> UUID.randomUUID())
                .toList();

        // generate random UUIDs so simulate records that do not exist..
        CaseworkerAssignRequest caseworkerAssignRequest = DataGenerator.createDefault(CaseworkerAssignRequestGenerator.class, builder -> {
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

        applicationAsserts.assertApplicationsMatchInRepository(expectedAlreadyAssignedApplications);
        applicationAsserts.assertApplicationsMatchInRepository(expectedUnassignedApplications);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenInvalidAssignRequestBecauseSomeApplicationsDoNotExist_whenAssignCaseworker_thenReturnNotFound_andAssignAvailableApplications_andGiveMissingIds() throws Exception {
        // given
        List<ApplicationEntity> expectedAssignedApplications = persistedDataGenerator.createAndPersistMultiple(ApplicationEntityGenerator.class,
                4,
                builder -> builder.caseworker(null));

        List<UUID> invalidApplicationIds = IntStream.range(0, 5)
                .mapToObj(_ -> UUID.randomUUID())
                .toList();

        List<UUID> allApplicationIds = Stream.of(
                        expectedAssignedApplications.stream().map(ApplicationEntity::getId),
                        invalidApplicationIds.stream()
                )
                .flatMap(s -> s)
                .collect(Collectors.toList());

        // generate random UUIDs so simulate records that do not exist..
        CaseworkerAssignRequest caseworkerAssignRequest = DataGenerator.createDefault(CaseworkerAssignRequestGenerator.class, builder -> {
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

        applicationAsserts.assertApplicationsMatchInRepository(expectedAssignedApplications);
    }

    @ParameterizedTest
    @MethodSource("invalidApplicationIdListsCases")
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenInvalidAssignmentRequestBecauseInvalidApplicationIds_whenAssignCaseworker_thenReturnBadRequest(
            List<UUID> invalidApplicationIdList
    ) throws Exception {
        // given
        ApplicationEntity expectedApplication = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> builder.caseworker(null));

        CaseworkerAssignRequest caseworkerAssignRequest = DataGenerator.createDefault(CaseworkerAssignRequestGenerator.class, builder -> {
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
        assertEquals(
                applicationAsserts.createApplicationIgnoreLastUpdated(expectedApplication),
                applicationAsserts.createApplicationIgnoreLastUpdated(actualApplication)
        );
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenInvalidAssignmentRequestBecauseCaseworkerDoesNotExist_whenAssignCaseworker_thenReturnBadRequest() throws Exception {
        // given
        ApplicationEntity expectedApplication = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> builder.caseworker(null));

        CaseworkerAssignRequest caseworkerAssignRequest = DataGenerator.createDefault(CaseworkerAssignRequestGenerator.class, builder -> {
            builder.caseworkerId(null)
                    .applicationIds(List.of(expectedApplication.getId()));
        });

        // when
        MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

        // then
        assertSecurityHeaders(result);
        assertBadRequest(result);

        ApplicationEntity actualApplication = applicationRepository.findById(expectedApplication.getId()).orElseThrow();
        assertNull(actualApplication.getCaseworker());
        assertEquals(
                applicationAsserts.createApplicationIgnoreLastUpdated(expectedApplication),
                applicationAsserts.createApplicationIgnoreLastUpdated(actualApplication)
        );
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenReaderRole_whenAssignCaseworker_thenReturnForbidden() throws Exception {
        // given
        CaseworkerAssignRequest caseworkerAssignRequest = DataGenerator.createDefault(CaseworkerAssignRequestGenerator.class);

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
        CaseworkerAssignRequest caseworkerAssignRequest = DataGenerator.createDefault(CaseworkerAssignRequestGenerator.class);

        // when
        MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER, caseworkerAssignRequest);

        // then
        assertSecurityHeaders(result);
        assertForbidden(result);
    }

    @Test
    public void givenNoUser_whenAssignCaseworker_thenReturnUnauthorised() throws Exception {
        // given
        CaseworkerAssignRequest caseworkerAssignRequest = DataGenerator.createDefault(CaseworkerAssignRequestGenerator.class);

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
                Arguments.of((Object)null),
                Arguments.of(Arrays.asList(new UUID[] { UUID.randomUUID(), null }))
        );
    }
}

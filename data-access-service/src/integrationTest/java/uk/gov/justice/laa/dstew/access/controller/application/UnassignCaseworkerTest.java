package uk.gov.justice.laa.dstew.access.controller.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.EventHistory;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerUnassignRequestGenerator;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNotFound;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

@ActiveProfiles("test")
public class UnassignCaseworkerTest extends BaseIntegrationTest {

    @ParameterizedTest
    @WithMockUser(authorities = TestConstants.Roles.READER)
    @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
    void givenValidUnassignRequestAndInvalidHeader_whenUnassignCaseworker_thenReturnBadRequest(
            String serviceName
    ) throws Exception {
        verifyServiceNameHeader(serviceName);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenValidUnassignRequestAndNoHeader_whenUnassignCaseworker_thenReturnBadRequest() throws Exception {
        verifyServiceNameHeader(null);
    }

    private void verifyServiceNameHeader(String serviceName) throws Exception {
        ApplicationEntity toUnassignedApplication = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe);
        });

        CaseworkerUnassignRequest caseworkerUnassignRequest = DataGenerator.createDefault(CaseworkerUnassignRequestGenerator.class, builder -> {
            builder.eventHistory(EventHistory.builder()
                    .eventDescription("Unassigned Caseworker")
                    .build());
        });
        ApplicationEntity expectedUnassignedApplication = toUnassignedApplication.toBuilder()
                .caseworker(null)
                .build();

        MvcResult result = postUri(TestConstants.URIs.UNASSIGN_CASEWORKER,
                                    caseworkerUnassignRequest,
                                    ServiceNameHeader(serviceName),
                                    expectedUnassignedApplication.getId());

        assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenValidUnassignRequest_whenUnassignCaseworker_thenReturnOK_andUnassignCaseworker() throws Exception {
        // given
        ApplicationEntity toUnassignedApplication = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe);
        });

        ApplicationEntity expectedUnassignedApplication = toUnassignedApplication.toBuilder()
                .caseworker(null)
                .build();

        CaseworkerUnassignRequest caseworkerUnassignRequest = DataGenerator.createDefault(CaseworkerUnassignRequestGenerator.class, builder -> {
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
        assertEquals(
                applicationAsserts.createApplicationIgnoreLastUpdated(expectedUnassignedApplication),
                applicationAsserts.createApplicationIgnoreLastUpdated(actual)
        );

        domainEventAsserts.assertDomainEventsCreatedForApplications(
                List.of(expectedUnassignedApplication),
                null,
                DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER,
                caseworkerUnassignRequest.getEventHistory()
        );
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenValidUnassignRequestWithBlankEventDescription_whenUnassignCaseworker_thenReturnOK_andUnassignCaseworker() throws Exception {
        // given
        ApplicationEntity toUnassignedApplication = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe);
        });

        ApplicationEntity expectedUnassignedApplication = toUnassignedApplication.toBuilder()
                .caseworker(null)
                .build();

        CaseworkerUnassignRequest caseworkerUnassignRequest = DataGenerator.createDefault(CaseworkerUnassignRequestGenerator.class, builder -> {
            builder.eventHistory(EventHistory.builder()
                    .eventDescription("")
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
        assertEquals(
                applicationAsserts.createApplicationIgnoreLastUpdated(expectedUnassignedApplication),
                applicationAsserts.createApplicationIgnoreLastUpdated(actual)
        );

        domainEventAsserts.assertDomainEventsCreatedForApplications(
                List.of(expectedUnassignedApplication),
                null,
                DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER,
                caseworkerUnassignRequest.getEventHistory()
        );
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenValidUnassignRequestWithNullEventDescription_whenUnassignCaseworker_thenReturnOK_andUnassignCaseworker() throws Exception {
        // given
        ApplicationEntity expectedUnassignedApplication = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe);
        });

        CaseworkerUnassignRequest caseworkerUnassignRequest = DataGenerator.createDefault(CaseworkerUnassignRequestGenerator.class, builder -> {
            builder.eventHistory(EventHistory.builder()
                    .eventDescription(null)
                    .build());
        });

        // when
        MvcResult result = postUri(TestConstants.URIs.UNASSIGN_CASEWORKER, caseworkerUnassignRequest, expectedUnassignedApplication.getId());

        // then
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);

        domainEventAsserts.assertDomainEventsCreatedForApplications(
                List.of(expectedUnassignedApplication),
                null,
                DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER,
                caseworkerUnassignRequest.getEventHistory()
        );
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenApplicationNotExist_whenUnassignCaseworker_thenReturnNotFound() throws Exception {
        // given
        CaseworkerUnassignRequest caseworkerUnassignRequest = DataGenerator.createDefault(CaseworkerUnassignRequestGenerator.class);

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
        ApplicationEntity expectedUnassignedApplication = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.caseworker(null);
        });

        CaseworkerUnassignRequest caseworkerUnassignRequest = DataGenerator.createDefault(CaseworkerUnassignRequestGenerator.class, builder -> {
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
        CaseworkerUnassignRequest caseworkerUnassignRequest = DataGenerator.createDefault(CaseworkerUnassignRequestGenerator.class);

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
        CaseworkerUnassignRequest caseworkerUnassignRequest = DataGenerator.createDefault(CaseworkerUnassignRequestGenerator.class);

        // when
        MvcResult result = postUri(TestConstants.URIs.UNASSIGN_CASEWORKER, caseworkerUnassignRequest, UUID.randomUUID().toString());

        // then
        assertSecurityHeaders(result);
        assertForbidden(result);
    }

    @Test
    public void givenNoUser_whenUnassignCaseworker_thenReturnUnauthorised() throws Exception {
        // given
        CaseworkerUnassignRequest caseworkerUnassignRequest = DataGenerator.createDefault(CaseworkerUnassignRequestGenerator.class);

        // when
        MvcResult result = postUri(TestConstants.URIs.UNASSIGN_CASEWORKER, caseworkerUnassignRequest, UUID.randomUUID().toString());

        // then
        assertSecurityHeaders(result);
        assertUnauthorised(result);
    }
}

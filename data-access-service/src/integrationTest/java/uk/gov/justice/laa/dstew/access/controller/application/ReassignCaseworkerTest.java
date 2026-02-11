package uk.gov.justice.laa.dstew.access.controller.application;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.EventHistory;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;

import java.util.List;
import java.util.stream.Collectors;

import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;

@ActiveProfiles("test")
public class ReassignCaseworkerTest extends BaseIntegrationTest {

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenValidReassignRequest_whenAssignCaseworker_thenReturnOK_andAssignCaseworker() throws Exception {
        // given
        List<ApplicationEntity> toReassignedApplications = persistedApplicationFactory.createAndPersistMultiple(
                4,
                builder -> {
                    builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe);
                });

        List<ApplicationEntity> expectedReassignedApplications = toReassignedApplications.stream()
                .peek(application -> application.setCaseworker(BaseIntegrationTest.CaseworkerJaneDoe))
                .toList();

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

            builder.caseworkerId(BaseIntegrationTest.CaseworkerJaneDoe.getId())
                    .applicationIds(toReassignedApplications.stream().map(ApplicationEntity::getId).collect(Collectors.toList()))
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

        applicationAsserts.assertApplicationsMatchInRepository(expectedReassignedApplications);
        applicationAsserts.assertApplicationsMatchInRepository(expectedAlreadyAssignedApplications);
        applicationAsserts.assertApplicationsMatchInRepository(expectedUnassignedApplications);
        domainEventAsserts.assertDomainEventsCreatedForApplications(
                toReassignedApplications,
                BaseIntegrationTest.CaseworkerJaneDoe.getId(),
                DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER,
                caseworkerReassignRequest.getEventHistory()
        );
    }
}

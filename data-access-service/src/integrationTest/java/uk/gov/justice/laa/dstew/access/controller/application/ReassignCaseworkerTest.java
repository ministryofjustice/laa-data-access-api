package uk.gov.justice.laa.dstew.access.controller.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
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

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;

@ActiveProfiles("test")
public class ReassignCaseworkerTest extends BaseIntegrationTest {

    @ParameterizedTest
    @WithMockUser(authorities = TestConstants.Roles.READER)
    @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
    void givenValidReassignRequestAndInvalidHeader_whenAssignCaseworker_thenReturnBadRequest(
            String serviceName
    ) throws Exception {
        verifyServiceNameHeader(serviceName);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenValidReassignRequestAndNoHeader_whenAssignCaseworker_thenReturnBadRequest() throws Exception {
        verifyServiceNameHeader(null);
    }

    private void verifyServiceNameHeader(String serviceName) throws Exception {
        List<ApplicationEntity> toReassignedApplications = persistedDataGenerator.createAndPersistMultiple(ApplicationEntityGenerator.class,
                4,
                builder -> builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe));

        CaseworkerAssignRequest caseworkerReassignRequest = DataGenerator.createDefault(CaseworkerAssignRequestGenerator.class, builder -> {
            builder.caseworkerId(BaseIntegrationTest.CaseworkerJaneDoe.getId())
                    .applicationIds(toReassignedApplications.stream().map(ApplicationEntity::getId).collect(Collectors.toList()))
                    .eventHistory(EventHistory.builder()
                            .eventDescription("Assigning caseworker")
                            .build());
        });

        MvcResult result = postUri(TestConstants.URIs.ASSIGN_CASEWORKER,
                                    caseworkerReassignRequest,
                                    ServiceNameHeader(serviceName));

        assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.WRITER)
    public void givenValidReassignRequest_whenAssignCaseworker_thenReturnOK_andAssignCaseworker() throws Exception {
        // given
        List<ApplicationEntity> toReassignedApplications = persistedDataGenerator.createAndPersistMultiple(ApplicationEntityGenerator.class,
                4,
                builder -> builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe));

        List<ApplicationEntity> expectedReassignedApplications = toReassignedApplications.stream()
                .peek(application -> application.setCaseworker(BaseIntegrationTest.CaseworkerJaneDoe))
                .toList();

        List<ApplicationEntity> expectedAlreadyAssignedApplications = persistedDataGenerator.createAndPersistMultiple(ApplicationEntityGenerator.class,
                5,
                builder -> builder.caseworker(BaseIntegrationTest.CaseworkerJaneDoe));

        List<ApplicationEntity> expectedUnassignedApplications = persistedDataGenerator.createAndPersistMultiple(ApplicationEntityGenerator.class,
                8,
                builder -> builder.caseworker(null));

        CaseworkerAssignRequest caseworkerReassignRequest = DataGenerator.createDefault(CaseworkerAssignRequestGenerator.class, builder -> {
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

package uk.gov.justice.laa.dstew.access.controller.application;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertContentHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNotFound;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

@ActiveProfiles("test")
public class GetApplicationTest extends BaseIntegrationTest {

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenExistingApplication_whenGetApplication_thenReturnOKWithCorrectData() throws Exception {
        // given
        DecisionEntity decision = persistedDataGenerator.createAndPersist(DecisionEntityGenerator.class, builder -> {
            builder.overallDecision(DecisionStatus.REFUSED);
        });
        ApplicationEntity application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder -> {
            builder.decision(decision);
            builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe);
        });

        Application expectedApplication = createApplication(application);

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, expectedApplication.getApplicationId());
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
        ApplicationEntity expectedApplication = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, expectedApplication.getId());

        // then
        assertSecurityHeaders(result);
        assertForbidden(result);
    }

    @Test
    public void givenNoUser_whenGetApplication_thenReturnUnauthorised() throws Exception {
        // given
        ApplicationEntity expectedApplication = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, expectedApplication.getId());

        // then
        assertSecurityHeaders(result);
        assertUnauthorised(result);
    }

    private Application createApplication(ApplicationEntity applicationEntity) {
        Application application = new Application();
        application.setApplicationId(applicationEntity.getId());
        application.setStatus(applicationEntity.getStatus());
        application.setLaaReference(applicationEntity.getLaaReference());
        if (applicationEntity.getCaseworker() != null) {
            application.setAssignedTo(applicationEntity.getCaseworker().getId());
        }
        application.setLastUpdated(OffsetDateTime.ofInstant(applicationEntity.getUpdatedAt(), ZoneOffset.UTC));
        application.setLastUpdated(OffsetDateTime.ofInstant(applicationEntity.getUpdatedAt(), ZoneOffset.UTC));
        application.setSubmittedAt(
            applicationEntity.getSubmittedAt() != null
                ? OffsetDateTime.ofInstant(applicationEntity.getSubmittedAt(), ZoneOffset.UTC)
                : null
        );
        application.setUseDelegatedFunctions(applicationEntity.getUsedDelegatedFunctions());
        application.setAutoGrant(applicationEntity.getIsAutoGranted());
        if (applicationEntity.getDecision() != null) {
            application.setOverallDecision(applicationEntity.getDecision().getOverallDecision());
        }
        return application;
    }
}

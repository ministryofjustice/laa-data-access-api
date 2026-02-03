package uk.gov.justice.laa.dstew.access.controller.application;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
        ApplicationEntity application = persistedApplicationFactory.createAndPersist();
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
        application.setApplicationId(applicationEntity.getId());
        application.setStatus(applicationEntity.getStatus());
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
        return application;
    }
}

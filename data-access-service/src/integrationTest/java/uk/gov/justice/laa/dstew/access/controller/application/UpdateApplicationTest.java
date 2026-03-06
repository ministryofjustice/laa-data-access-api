package uk.gov.justice.laa.dstew.access.controller.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.builders.HttpHeadersBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertBadRequest;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoContent;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNotFound;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UpdateApplicationTest extends BaseIntegrationTest {

    @ParameterizedTest
    @WithMockUser(authorities = TestConstants.Roles.READER)
    @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
    void givenValidApplicationDataAndIncorrectHeader_whenUpdateApplication_thenReturnBadRequest(
            String serviceName
    ) throws Exception {
        verifyBadServiceNameHeader(serviceName);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenValidApplicationDataAndIncorrectHeader_whenUpdateApplication_thenReturnBadRequest() throws Exception {
        verifyBadServiceNameHeader(null);
    }

    private void verifyBadServiceNameHeader(String serviceName) throws Exception {

        ApplicationUpdateRequest applicationUpdateRequest = applicationUpdateRequestFactory.create(builder -> {
            builder.applicationContent(
                    new HashMap<>(Map.of("test", "changed"))).status(ApplicationStatus.APPLICATION_SUBMITTED);
        });

        MvcResult result = patchUri(TestConstants.URIs.UPDATE_APPLICATION,
                                    applicationUpdateRequest,
                                    ServiceNameHeader(serviceName),
                                    UUID.randomUUID());
        applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
    }

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
            builder.applicationContent(expectedContent).status(ApplicationStatus.APPLICATION_SUBMITTED);
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
        assertEquals(ApplicationStatus.APPLICATION_SUBMITTED, actual.getStatus());
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

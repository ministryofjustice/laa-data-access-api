package uk.gov.justice.laa.dstew.access.controller;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.HeaderUtils;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ApplicationAsserts.assertApplicationEqual;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.*;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

@ActiveProfiles("test")
public class ApplicationTest extends BaseIntegrationTest {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class GetApplication {

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void given_existing_data_when_get_called_then_OK_with_correct_data() throws Exception {

            // given
            ApplicationEntity expected = persistedApplicationFactory.createAndPersist();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, expected.getId());
            Application actual = deserialise(result, Application.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertApplicationEqual(expected, actual);
        }

        // TODO: check whether this should return application/problem+json
        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void given_no_data_when_get_called_then_NotFound() throws Exception {
            // given
            UUID id = UUID.randomUUID();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, id);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertNotFound(result);
        }

        // TODO: Identify what the problem record should be
        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void given_invalid_id_in_url_when_get_called_then_NotFound() throws Exception {
            // given
            String id = "not an id";

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, id);

            // then
            assertSecurityHeaders(result);
            assertBadRequest(result);
        }

        // TODO: Identify what the problem record should be
        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void given_malformed_url_when_get_called_then_BadRequest() throws Exception {
            // given
            // no data

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, ""); // not passing in an ID.

            // then
            assertSecurityHeaders(result);
            assertBadRequest(result);

            assertTrue(false);
        }

        // TODO: Identify what the problem record should be
        @Test
        @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
        public void given_unknown_role_when_get_called_then_Forbidden() throws Exception {
            // given
            // no data

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, UUID.randomUUID());

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }

        // TODO: Identify what the problem record should be
        @Test
        public void given_no_user_when_get_called_then_Unauthorized() throws Exception {
            // given
            // no data

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, UUID.randomUUID());

            // then
            assertSecurityHeaders(result);
            assertUnauthorised(result);
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class CreateApplication {
        @Test
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void given_data_when_calling_create_data_is_created_with_OK() throws Exception {

            // given
            ApplicationCreateRequest request = applicationCreateRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);

            // then
            assertSecurityHeaders(result);
            assertCreated(result);

            UUID storedId = UUID.fromString(HeaderUtils.GetUUIDFromLocation(
                    result.getResponse().getHeader("Location")
            ));
            ApplicationEntity stored = applicationRepository.findById(storedId).orElseThrow(() -> new ApplicationNotFoundException(storedId.toString()));
            assertApplicationEqual(request, stored);
        }

        // TODO: think about how a status outside of the range of the Enum can be sent. Can a client actually do this anyway?
        @ParameterizedTest
        @MethodSource("applicationCreateRequestInvalidDataCases")
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void given_invalid_data_when_calling_create_call_fails_with_BadRequest(ApplicationCreateRequest request) throws Exception {
            // given
            // in ValueSource

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);

            // then
            assertSecurityHeaders(result);
            assertBadRequest(result);
        }

        @ParameterizedTest
        @ValueSource(strings = { "", "{}" })
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void given_no_body_when_calling_create_call_fails_created_with_BadRequest(String request) throws Exception {
            // given
            // in ValueSource

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);

            // then
            assertSecurityHeaders(result);
            assertBadRequest(result);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void given_data_and_reader_role_when_calling_create_call_fails_with_Forbidden() throws Exception {
            // given
            ApplicationCreateRequest request = applicationCreateRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }

        @Test
        public void given_data_and_no_auth_when_calling_create_call_fails_with_Unauthorized() throws Exception {
            // given
            ApplicationCreateRequest request = applicationCreateRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);

            // then
            assertSecurityHeaders(result);
            assertUnauthorised(result);
        }

        // TODO: figure out how to check that the logs do not contain PII
        @Test
        public void given_data_and_error_when_calling_create_call_fails_and_omits_PII_from_logs() throws Exception {
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
                    })),
                    Arguments.of(applicationCreateRequestFactory.create(builder -> {
                        builder.applicationContent(null);
                    }))
            );
        }
    }
}
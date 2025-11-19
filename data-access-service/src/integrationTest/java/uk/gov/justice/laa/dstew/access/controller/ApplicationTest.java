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
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.HeaderUtils;
import uk.gov.justice.laa.dstew.access.utils.ProblemDetailBuilder;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ApplicationAsserts.assertApplicationEqual;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.*;

@ActiveProfiles("test")
public class ApplicationTest extends BaseIntegrationTest {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class GetApplication {

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void givenExistingData_whenGetCalled_thenOKWithCorrectData() throws Exception {

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
        public void givenNoData_whenGetCalled_thenNotFound() throws Exception {
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
        public void givenInvalidIdInUrl_whenGetCalled_thenNotFound() throws Exception {
            // given
            String id = "not an id";

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, id);
            ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            assertProblemRecord(HttpStatus.BAD_REQUEST, "", "", result, detail);
        }

        // TODO: Identify what the problem record should be
        @Test
        @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
        public void givenUnknownRole_whenGetCalled_thenForbidden() throws Exception {
            // given
            // no data

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, UUID.randomUUID());
            ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            assertProblemRecord(HttpStatus.FORBIDDEN, "", "", result, detail);
        }

        // TODO: Identify what the problem record should be
        @Test
        public void givenNoUser_whenGetCalled_thenUnauthorized() throws Exception {
            // given
            // no data

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, UUID.randomUUID());
            ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            assertProblemRecord(HttpStatus.FORBIDDEN, "", "", result, detail);
        }
    }

    /**
     * Tests related to DSTEW-503
     */
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class CreateApplication {
        @Test
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenData_whenCallingCreateData_thenCreatedWithOK() throws Exception {

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
        // TODO: check problem details
        @ParameterizedTest
        @MethodSource("applicationCreateRequestInvalidDataCases")
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenInvalidData_whenCallingCreate_thenCallFailsWithBadRequest_andRepositoryEmpty(
                ApplicationCreateRequest request,
                ProblemDetail expectedDetail) throws Exception {

            // given
            // in MethodSource

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);
            ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            assertProblemRecord(HttpStatus.BAD_REQUEST, expectedDetail, result, detail);

            // and
            assertEquals(0, applicationRepository.count());
        }

        // TODO: check problem details
        @ParameterizedTest
        @ValueSource(strings = { "", "{}" })
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenNoBody_whenCallingCreate_thenCallFailsWithBadRequest_andRepositoryEmpty(String request) throws Exception {
            // given
            // in ValueSource

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);
            ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            assertProblemRecord(HttpStatus.BAD_REQUEST, "Bad Request", "Failed to read request", result, detail);

            // and
            assertEquals(0, applicationRepository.count());
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void givenDataAndReaderRole_whenCallingCreate_thenFailsWithForbidden_andRepositoryEmpty() throws Exception {
            // given
            ApplicationCreateRequest request = applicationCreateRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);
            ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            assertProblemRecord(HttpStatus.FORBIDDEN, "", "", result, detail);

            // and
            assertEquals(0, applicationRepository.count());
        }

        @Test
        public void givenDataAndNoAuth_whenCallingCreate_thenFailsWithUnauthorized_andRepositoryEmpty() throws Exception {
            // given
            ApplicationCreateRequest request = applicationCreateRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);
            ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            assertProblemRecord(HttpStatus.UNAUTHORIZED, "", "", result, detail);

            // and
            assertEquals(0, applicationRepository.count());
        }

        // TODO: figure out how to check that the logs do not contain PII
        @Test
        public void givenDataAndError_whenCallingCreate_thenFailsAndOmitsPIIFromLogs_andRepositoryEmpty() throws Exception {
            // given
            ApplicationCreateRequest request = applicationCreateRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);
            ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            assertProblemRecord(HttpStatus.UNAUTHORIZED, "", "", result, detail);

            // and
            assertEquals(0, applicationRepository.count());
        }

        private Stream<Arguments> applicationCreateRequestInvalidDataCases() {
            return Stream.of(
                    Arguments.of(applicationCreateRequestFactory.create(builder -> {
                        builder.status(null);
                    }), ProblemDetailBuilder
                            .create()
                            .status(HttpStatus.BAD_REQUEST)
                            .title("Bad Request")
                            .detail("detail")
                            .build()
                    ),
                    Arguments.of(applicationCreateRequestFactory.create(builder -> {
                        builder.applicationContent(null);
                    }), ProblemDetailBuilder
                            .create()
                            .status(HttpStatus.BAD_REQUEST)
                            .title("Bad Request")
                            .detail("detail")
                            .build()
                    )
            );
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class UpdateApplication {}

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class GetAllApplications {}
}
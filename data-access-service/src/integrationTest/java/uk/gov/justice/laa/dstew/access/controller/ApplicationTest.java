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
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.HeaderUtils;
import uk.gov.justice.laa.dstew.access.utils.ProblemDetailBuilder;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.uriBuilders.GetAllApplicationsURIBuilder;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ApplicationAsserts.assertApplicationEqual;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ApplicationAsserts.assertApplicationListsEqual;
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

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void givenNoData_whenGetCalled_thenNotFound() throws Exception {
            // given
            UUID id = UUID.randomUUID();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, id);

            // then
            // TODO: check whether the 404 should be returning application/problem+json
            //assertContentHeaders(result);
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
            //ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            //assertProblemRecord(HttpStatus.BAD_REQUEST, "", "", result, detail);
            // TODO: remove this assert as it is checked in the method above
            assertBadRequest(result);
        }

        // TODO: Identify what the problem record should be
        @Test
        @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
        public void givenUnknownRole_whenGetCalled_thenForbidden() throws Exception {
            // given
            // no data

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, UUID.randomUUID());
            //ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            //assertProblemRecord(HttpStatus.FORBIDDEN, "", "", result, detail);
            // TODO: remove this assert as it is checked in the method above
            assertForbidden(result);
        }

        // TODO: Identify what the problem record should be
        @Test
        public void givenNoUser_whenGetCalled_thenUnauthorized() throws Exception {
            // given
            // no data

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, UUID.randomUUID());
            //ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            //assertProblemRecord(HttpStatus.UNAUTHORIZED, "", "", result, detail);
            // TODO: remove this assert as it is checked in the method above
            assertUnauthorised(result);
        }
    }

    /**
     * Tests related to DSTEW-503
     */
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class CreateApplication {

        // TODO: check whether endpoint should also return a body (DSTEW-503)
        @Test
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenData_whenCallingCreateData_thenCreatedWithOK() throws Exception {

            // given
            ApplicationCreateRequest expected = applicationCreateRequestFactory.create();

            // when
            MvcResult response = postUri(TestConstants.URIs.CREATE_APPLICATION, expected);

            // then
            assertSecurityHeaders(response);
            assertCreated(response);

            UUID storedId = UUID.fromString(HeaderUtils.GetUUIDFromLocation(
                    response.getResponse().getHeader("Location")
            ));
            ApplicationEntity actual = applicationRepository.findById(storedId).orElseThrow(() -> new ApplicationNotFoundException(storedId.toString()));
            assertApplicationEqual(expected, actual);
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
            //ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            //assertProblemRecord(HttpStatus.FORBIDDEN, "", "", result, detail);

            // and
            assertEquals(0, applicationRepository.count());
            // TODO: remove this assert as it is checked in the assertProblemRecord method above
            assertForbidden(result);
        }

        @Test
        public void givenDataAndNoAuth_whenCallingCreate_thenFailsWithUnauthorized_andRepositoryEmpty() throws Exception {
            // given
            ApplicationCreateRequest request = applicationCreateRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);
            //ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            //assertProblemRecord(HttpStatus.UNAUTHORIZED, "", "", result, detail);

            // and
            assertEquals(0, applicationRepository.count());
            // TODO: check whether we remove this assert as it is checked in the assertProblemRecord above
            assertUnauthorised(result);
        }

        // TODO: figure out how to check that the logs do not contain PII
        @Test
        public void givenDataAndError_whenCallingCreate_thenFailsAndOmitsPIIFromLogs_andRepositoryEmpty() throws Exception {
            // given
            ApplicationCreateRequest request = applicationCreateRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);
            //ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            //assertProblemRecord(HttpStatus.UNAUTHORIZED, "", "", result, detail);

            // and
            assertEquals(0, applicationRepository.count());
            // TODO: check whether we remove this assert as it is checked in the assertProblemRecord above
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
                        builder.applicationContent(null);
                    }), ProblemDetailBuilder
                            .create()
                            .status(HttpStatus.BAD_REQUEST)
                            .title("Validation failed")
                            .detail("One or more validation rules were violated")
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
    class GetAllApplications {

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenSevenApplications_whenGetAllCalled_thenReturnPageOfSevenApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(7, builder -> {
                builder.status(ApplicationStatus.IN_PROGRESS);
            });

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_ALL_APPLICATIONS);
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertThat(actual.getPaging().getTotalRecords())
                    .isEqualTo(expectedApplications.size());
            assertThat(actual.getPaging().getPageSize()).isEqualTo(10);
            assertThat(actual.getPaging().getPage()).isEqualTo(0);
            assertThat(actual.getPaging().getItemsReturned()).isEqualTo(7);
            assertApplicationListsEqual(expectedApplications, actual.getApplications());
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenTwentyApplications_whenGetAllCalled_thenReturnPageOfTenApplications() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(10, builder -> {
                builder.status(ApplicationStatus.IN_PROGRESS);
            });
            List<ApplicationEntity> unexpectedApplications = persistedApplicationFactory.createAndPersistMultiple(10, builder -> {
                builder.status(ApplicationStatus.SUBMITTED);
            });

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_ALL_APPLICATIONS);
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertThat(actual.getPaging().getTotalRecords())
                    .isEqualTo(expectedApplications.size() + unexpectedApplications.size());
            assertThat(actual.getPaging().getPageSize()).isEqualTo(10);
            assertThat(actual.getPaging().getPage()).isEqualTo(0);
            assertThat(actual.getPaging().getItemsReturned()).isEqualTo(10);
            assertApplicationListsEqual(expectedApplications, actual.getApplications());
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenTwentyApplicationsAndFilterForInProgress_whenGetAllCalled_thenReturnPageOfTenApplications() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(10, builder -> {
                builder.status(ApplicationStatus.IN_PROGRESS);
            });
            persistedApplicationFactory.createAndPersistMultiple(10, builder -> {
                builder.status(ApplicationStatus.SUBMITTED);
            });

            // when
            MvcResult result = getUri(new GetAllApplicationsURIBuilder().withStatusFilter(ApplicationStatus.IN_PROGRESS).build());
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertThat(actual.getPaging().getTotalRecords())
                    .isEqualTo(expectedApplications.size());
            assertThat(actual.getPaging().getPageSize()).isEqualTo(10);
            assertThat(actual.getPaging().getPage()).isEqualTo(0);
            assertThat(actual.getPaging().getItemsReturned()).isEqualTo(10);
            assertApplicationListsEqual(expectedApplications, actual.getApplications());
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenThirtyApplicationsAndFilterForSubmitted_whenGetAllCalled_thenReturnFirstPageOfTenApplications() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(20, builder -> {
                builder.status(ApplicationStatus.SUBMITTED);
            });
            persistedApplicationFactory.createAndPersistMultiple(10, builder -> {
                builder.status(ApplicationStatus.IN_PROGRESS);
            });

            // when
            MvcResult result = getUri(new GetAllApplicationsURIBuilder()
                    .withStatusFilter(ApplicationStatus.SUBMITTED)
                    .build());
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertThat(actual.getPaging().getTotalRecords())
                    .isEqualTo(expectedApplications.size());
            assertThat(actual.getPaging().getPageSize()).isEqualTo(10);
            assertThat(actual.getPaging().getPage()).isEqualTo(0);
            assertThat(actual.getPaging().getItemsReturned()).isEqualTo(10);
            assertApplicationListsEqual(expectedApplications.subList(0, 10), actual.getApplications());
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenThirtyApplicationsAndFilterForSubmittedAndPageTwo_whenGetAllCalled_thenReturnSecondPageOfTenApplications() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(20, builder -> {
                builder.status(ApplicationStatus.SUBMITTED);
            });
            persistedApplicationFactory.createAndPersistMultiple(10, builder -> {
                builder.status(ApplicationStatus.IN_PROGRESS);
            });

            // when
            MvcResult result = getUri(new GetAllApplicationsURIBuilder()
                    .withStatusFilter(ApplicationStatus.SUBMITTED)
                    .withPageNumber(1)
                    .build());
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertThat(actual.getPaging().getTotalRecords())
                    .isEqualTo(expectedApplications.size());
            assertThat(actual.getPaging().getPageSize()).isEqualTo(10);
            assertThat(actual.getPaging().getPage()).isEqualTo(1);
            assertThat(actual.getPaging().getItemsReturned()).isEqualTo(10);
            assertApplicationListsEqual(expectedApplications.subList(10, 20), actual.getApplications());
        }
    }
}
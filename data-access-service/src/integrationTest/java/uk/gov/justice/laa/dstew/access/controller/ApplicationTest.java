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
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.dstew.access.model.*;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.HeaderUtils;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.builders.ProblemDetailBuilder;
import uk.gov.justice.laa.dstew.access.utils.builders.ValidationExceptionBuilder;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ApplicationAsserts.assertApplicationEqual;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ApplicationAsserts.assertApplicationListsEqual;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.*;

@ActiveProfiles("test")
public class ApplicationTest extends BaseIntegrationTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class GetApplication {

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void givenExistingApplication_whenGetApplication_thenReturnOKWithCorrectData() throws Exception {
            // given
            ApplicationEntity expectedApplication = persistedApplicationFactory.createAndPersist();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, expectedApplication.getId());
            Application actualApplication = deserialise(result, Application.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertApplicationEqual(expectedApplication, actualApplication);
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
            var json = objectMapper.readTree(result.getResponse().getContentAsString());
            assertEquals("No application found with id: " + notExistApplicationId, json.get("detail").asText());

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
        public void givenNoUser_whenGetApplication_thenReturnUnauthorized() throws Exception {
            // given
            ApplicationEntity expectedApplication = persistedApplicationFactory.createAndPersist();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, expectedApplication.getId());

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
        public void givenCreateNewApplication_whenCreateApplication_thenReturnCreatedWithLocationHeader() throws Exception {

            // given
            ApplicationCreateRequest applicationCreateRequest = applicationCreateRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, applicationCreateRequest);

            // then
            assertSecurityHeaders(result);
            assertCreated(result);

            UUID createdApplicationId = HeaderUtils.GetUUIDFromLocation(
                    result.getResponse().getHeader("Location")
            );
            assertNotNull(createdApplicationId);
            ApplicationEntity createdApplication = applicationRepository.findById(createdApplicationId).orElseThrow(() -> new ApplicationNotFoundException(createdApplicationId.toString()));
            assertApplicationEqual(applicationCreateRequest, createdApplication);
        }

        @ParameterizedTest
        @MethodSource("applicationCreateRequestInvalidDataCases")
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenInvalidApplicationRequestData_whenCreateApplication_thenReturnBadRequest(
                ApplicationCreateRequest request,
                ProblemDetail expectedDetail) throws Exception {
            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);
            ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            assertProblemRecord(HttpStatus.BAD_REQUEST, expectedDetail, result, detail);
            assertEquals(0, applicationRepository.count());
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenInvalidApplicationContent_whenCreateApplication_thenReturnBadRequest() throws Exception {
            ApplicationCreateRequest applicationCreateRequest = applicationCreateRequestFactory.create(builder -> {
                builder.applicationContent(null);
            });

            ValidationException expectedValidationException = ValidationExceptionBuilder
                    .create()
                    .errors(List.of("Application content cannot be empty"))
                    .build();

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, applicationCreateRequest);
            ValidationException validationException = objectMapper.readValue(result.getResponse().getContentAsString(), ValidationException.class);

            // then
            assertSecurityHeaders(result);
            assertValidationException(HttpStatus.BAD_REQUEST, expectedValidationException.errors(), result, validationException);
            assertEquals(0, applicationRepository.count());
        }

        @ParameterizedTest
        @ValueSource(strings = { "", "{}" })
        @WithMockUser(authorities = TestConstants.Roles.WRITER)
        public void givenNoRequestBody_whenCreateApplication_thenReturnBadRequest(String request) throws Exception {
            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);
            ProblemDetail detail = objectMapper.readValue(result.getResponse().getContentAsString(), ProblemDetail.class);

            // then
            assertSecurityHeaders(result);
            assertProblemRecord(HttpStatus.BAD_REQUEST, "Bad Request", "Failed to read request", result, detail);
            assertEquals(0, applicationRepository.count());
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        public void givenDataAndReaderRole_whenCreateApplication_thenReturnForbidden() throws Exception {
            // given
            ApplicationCreateRequest request = applicationCreateRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }

        @Test
        public void givenDataAndNoAuth_whenCreateApplication_thenReturnUnauthorized() throws Exception {
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
                            }), ProblemDetailBuilder
                                    .create()
                                    .status(HttpStatus.BAD_REQUEST)
                                    .title("Bad Request")
                                    .detail("Invalid request content.")
                                    .build()
                    ),
                    Arguments.of(applicationCreateRequestFactory.create(builder -> {
                                builder.applicationReference(null);
                            }), ProblemDetailBuilder
                                    .create()
                                    .status(HttpStatus.BAD_REQUEST)
                                    .title("Bad Request")
                                    .detail("Invalid request content.")
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
    class GetApplications {

        public static final String PAGE_PARAM = "page=";
        public static final String PAGE_SIZE_PARAM = "pagesize=";
        public static final String STATUS_PARAM = "status=";
        public static final String FIRSTNAME_PARAM = "firstname=";
        public static final String LASTNAME_PARAM = "lastname=";

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsWithoutFiltering_whenGetApplications_thenReturnApplicationsWithPagingCorrectly() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(7, builder -> {
                builder.status(ApplicationStatus.IN_PROGRESS);
            });

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);
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
            assertThat(actual.getApplications().size()).isEqualTo(7);
            assertApplicationListsEqual(expectedApplications, actual.getApplications());
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsRequiringPageTwo_whenGetApplication_thenReturnSecondPageOfApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(20, builder -> {
                builder.status(ApplicationStatus.IN_PROGRESS);
            });

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + PAGE_PARAM + "1");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertThat(actual.getPaging().getTotalRecords()).isEqualTo(20);
            assertThat(actual.getPaging().getPageSize()).isEqualTo(10);
            assertThat(actual.getPaging().getPage()).isEqualTo(1);
            assertThat(actual.getPaging().getItemsReturned()).isEqualTo(10);
            assertThat(actual.getApplications().size()).isEqualTo(10);
            assertThat((actual.getApplications()).containsAll(expectedApplications.subList(9,19)));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByInProgressStatus_whenGetApplication_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(5, builder -> {
                builder.status(ApplicationStatus.IN_PROGRESS);
            });
            persistedApplicationFactory.createAndPersistMultiple(10, builder -> {
                builder.status(ApplicationStatus.SUBMITTED);
            });

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + STATUS_PARAM + ApplicationStatus.IN_PROGRESS);
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertThat(actual.getPaging().getTotalRecords()).isEqualTo(expectedApplications.size());
            assertThat(actual.getPaging().getPageSize()).isEqualTo(10);
            assertThat(actual.getPaging().getPage()).isEqualTo(0);
            assertThat(actual.getPaging().getItemsReturned()).isEqualTo(5);
            assertThat(actual.getApplications().size()).isEqualTo(5);
            assertThat((actual.getApplications()).containsAll(expectedApplications));
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
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);
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
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);
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

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenTwentyApplicationsInProgressAndFilterForSubmitted_whenGetAllCalled_thenNoRecordsReturned() throws Exception {
            // given
            persistedApplicationFactory.createAndPersistMultiple(20, builder -> {
                builder.status(ApplicationStatus.IN_PROGRESS);
            });

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertThat(actual.getPaging().getTotalRecords())
                    .isEqualTo(0);
            assertThat(actual.getPaging().getPageSize()).isEqualTo(10);
            assertThat(actual.getPaging().getPage()).isEqualTo(0);
            assertThat(actual.getPaging().getItemsReturned()).isEqualTo(0);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenTwentyApplicationsAndPageSizeOfTwenty_whenGetAllCalled_thenTwentyRecordsReturned() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(20, builder -> {
                builder.status(ApplicationStatus.IN_PROGRESS);
            });
            persistedApplicationFactory.createAndPersistMultiple(10, builder -> {
                builder.status(ApplicationStatus.SUBMITTED);
            });

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertThat(actual.getPaging().getTotalRecords())
                    .isEqualTo(30);
            assertThat(actual.getPaging().getPageSize()).isEqualTo(20);
            assertThat(actual.getPaging().getPage()).isEqualTo(0);
            assertThat(actual.getPaging().getItemsReturned()).isEqualTo(20);
            assertApplicationListsEqual(expectedApplications, actual.getApplications());
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenTenApplicationsForJohnDoe_whenGetAllCalled_thenTenRecordsReturned() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(20, builder -> {
                builder.status(ApplicationStatus.IN_PROGRESS);
                builder.individuals(Set.of(
                        individualFactory.create(individualBuilder -> {
                            individualBuilder.firstName("John");
                        })
                ));
            });
            persistedApplicationFactory.createAndPersistMultiple(10, builder -> {
                builder.status(ApplicationStatus.IN_PROGRESS);
                builder.individuals(Set.of(
                        individualFactory.create(individualBuilder -> {
                            individualBuilder.firstName("Jane");
                        })
                ));
            });

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertThat(actual.getPaging().getTotalRecords())
                    .isEqualTo(20);
            assertThat(actual.getPaging().getPageSize()).isEqualTo(10);
            assertThat(actual.getPaging().getPage()).isEqualTo(0);
            assertThat(actual.getPaging().getItemsReturned()).isEqualTo(10);
            assertApplicationListsEqual(expectedApplications.subList(0,10), actual.getApplications());
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenTenApplicationsForJaneDoeInStatusSubmitted_whenGetAllCalled_thenTenRecordsReturned() throws Exception {
            // given
            persistedApplicationFactory.createAndPersistMultiple(20, builder -> {
                builder.status(ApplicationStatus.IN_PROGRESS);
                builder.individuals(Set.of(
                        individualFactory.create(individualBuilder -> {
                            individualBuilder.firstName("Jane");
                        })
                ));
            });
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(10, builder -> {
                builder.status(ApplicationStatus.SUBMITTED);
                builder.individuals(Set.of(
                        individualFactory.create(individualBuilder -> {
                            individualBuilder.firstName("Jane");
                        })
                ));
            });

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertThat(actual.getPaging().getTotalRecords())
                    .isEqualTo(10);
            assertThat(actual.getPaging().getPageSize()).isEqualTo(10);
            assertThat(actual.getPaging().getPage()).isEqualTo(0);
            assertThat(actual.getPaging().getItemsReturned()).isEqualTo(10);
            assertApplicationListsEqual(expectedApplications.subList(0,10), actual.getApplications());
        }

        @Test
        public void givenNoUser_whenGetAllCalled_thenReturnUnauthorised() throws Exception {
            // given
            // nothing

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);

            // then
            assertSecurityHeaders(result);
            assertUnauthorised(result);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
        public void givenNoRole_whenGetAllCalled_thenReturnForbidden() throws Exception {
            // given
            // nothing

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }
    }
}
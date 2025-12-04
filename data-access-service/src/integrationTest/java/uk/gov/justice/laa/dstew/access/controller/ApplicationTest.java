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
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.*;

@ActiveProfiles("test")
public class ApplicationTest extends BaseIntegrationTest {
    private static final int applicationVersion = 1;

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
        public void givenNoUser_whenGetApplication_thenReturnUnauthorised() throws Exception {
            // given
            ApplicationEntity expectedApplication = persistedApplicationFactory.createAndPersist();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATION, expectedApplication.getId());

            // then
            assertSecurityHeaders(result);
            assertUnauthorised(result);
        }

        private void assertApplicationEqual(ApplicationEntity expected, Application actual) {
            assertEquals(expected.getId(), actual.getId());
            assertEquals(expected.getApplicationContent(), actual.getApplicationContent());
            assertEquals(expected.getStatus(), actual.getApplicationStatus());
            assertEquals(expected.getSchemaVersion(), actual.getSchemaVersion());
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
        public void givenCorrectRequestBodyAndReaderRole_whenCreateApplication_thenReturnForbidden() throws Exception {
            // given
            ApplicationCreateRequest request = applicationCreateRequestFactory.create();

            // when
            MvcResult result = postUri(TestConstants.URIs.CREATE_APPLICATION, request);

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }

        @Test
        public void givenCorrectRequestBodyAndNoAuthentication_whenCreateApplication_thenReturnUnauthorised() throws Exception {
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
                                    .title("Validation failed")
                                    .detail("One or more validation rules were violated")
                                    .build()
                    ),
                    Arguments.of(applicationCreateRequestFactory.create(builder -> {
                                builder.applicationReference(null);
                            }), ProblemDetailBuilder
                                    .create()
                                    .status(HttpStatus.BAD_REQUEST)
                                    .title("Validation failed")
                                    .detail("One or more validation rules were violated")
                                    .build()
                    )
            );
        }

        private void assertApplicationEqual(ApplicationCreateRequest expected, ApplicationEntity actual) {
            assertNotNull(actual.getId());
            assertEquals(expected.getApplicationReference(), actual.getApplicationReference());
            assertEquals(expected.getApplicationContent(), actual.getApplicationContent());
            assertEquals(expected.getStatus(), actual.getStatus());
            assertEquals(applicationVersion, actual.getSchemaVersion());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class UpdateApplication {}

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class GetApplications {

        public static final String SEARCH_PAGE_PARAM = "page=";
        public static final String SEARCH_PAGE_SIZE_PARAM = "pagesize=";
        public static final String SEARCH_STATUS_PARAM = "status=";
        public static final String SEARCH_FIRSTNAME_PARAM = "firstname=";
        public static final String SEARCH_LASTNAME_PARAM = "lastname=";

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsWithoutFiltering_whenGetApplications_thenReturnApplicationsWithPagingCorrectly() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(7, builder ->
                builder.status(ApplicationStatus.IN_PROGRESS));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 7, 10,0,7);
            assertThat(actual.getApplications().size()).isEqualTo(7);
            assertThat((actual.getApplications()).containsAll(expectedApplications));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsRequiringPageTwo_whenGetApplications_thenReturnSecondPageOfApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(20, builder ->
                builder.status(ApplicationStatus.IN_PROGRESS));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_PAGE_PARAM + "1");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 20, 10,1,10);
            assertThat(actual.getApplications().size()).isEqualTo(10);
            assertThat((actual.getApplications()).containsAll(expectedApplications.subList(9,19)));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsAndPageSizeOfTwenty_whenGetApplications_thenReturnTwentyRecords() throws Exception {
            // given
            List<ApplicationEntity> inProgressApplications = persistedApplicationFactory.createAndPersistMultiple(15, builder ->
                    builder.status(ApplicationStatus.IN_PROGRESS));
            List<ApplicationEntity> submittedApplications = persistedApplicationFactory.createAndPersistMultiple(10, builder ->
                    builder.status(ApplicationStatus.SUBMITTED));

            List<ApplicationEntity> expectedApplications = Stream.concat(inProgressApplications.stream(), submittedApplications.stream().limit(5)).toList();

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_PAGE_SIZE_PARAM + "20");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 25, 20,0,20);
            assertThat(actual.getApplications().size()).isEqualTo(20);
            assertThat((actual.getApplications()).containsAll(expectedApplications));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByInProgressStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(5, builder ->
                builder.status(ApplicationStatus.IN_PROGRESS));
            persistedApplicationFactory.createAndPersistMultiple(10, builder ->
                builder.status(ApplicationStatus.SUBMITTED));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.IN_PROGRESS);
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 5, 10,0,5);
            assertThat(actual.getApplications().size()).isEqualTo(5);
            assertThat((actual.getApplications()).containsAll(expectedApplications));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredBySubmittedStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(6, builder ->
                builder.status(ApplicationStatus.SUBMITTED));
            persistedApplicationFactory.createAndPersistMultiple(10, builder ->
                builder.status(ApplicationStatus.IN_PROGRESS));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.SUBMITTED);
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 6, 10,0,6);
            assertThat(actual.getApplications().size()).isEqualTo(6);
            assertThat((actual.getApplications()).containsAll(expectedApplications));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredBySubmittedStatusWithPaging_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(17, builder ->
                builder.status(ApplicationStatus.SUBMITTED));
            persistedApplicationFactory.createAndPersistMultiple(10, builder ->
                builder.status(ApplicationStatus.IN_PROGRESS));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                    + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.SUBMITTED
                    + "&" + SEARCH_PAGE_PARAM + "1");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 17, 10,1,7);
            assertThat(actual.getApplications().size()).isEqualTo(7);
            assertThat((actual.getApplications()).containsAll(expectedApplications.subList(10,17)));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByFirstName_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                    builder.individuals(Set.of(individualFactory.create(i -> i.firstName("John")))));
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(5, builder ->
                    builder.individuals(Set.of(individualFactory.create(i -> i.firstName("Jane")))));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_FIRSTNAME_PARAM + "Jane");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 5, 10,0,5);
            assertThat(actual.getApplications().size()).isEqualTo(5);
            assertThat((actual.getApplications()).containsAll(expectedApplications));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByFirstNameAndStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            persistedApplicationFactory.createAndPersistMultiple(8, builder ->
                builder
                    .status(ApplicationStatus.IN_PROGRESS)
                    .individuals(Set.of(individualFactory.create(i -> i.firstName("Jane")))));
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(7, builder ->
                builder
                    .status(ApplicationStatus.SUBMITTED)
                    .individuals(Set.of(individualFactory.create(i -> i.firstName("Jane")))));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                    + "?"  + SEARCH_STATUS_PARAM + ApplicationStatus.SUBMITTED
                    + "&" + SEARCH_FIRSTNAME_PARAM + "Jane");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 7, 10,0,7);
            assertThat(actual.getApplications().size()).isEqualTo(7);
            assertThat((actual.getApplications()).containsAll(expectedApplications));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByLastName_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                builder.individuals(Set.of(individualFactory.create(i -> i.lastName("David")))));
            persistedApplicationFactory.createAndPersistMultiple(5, builder ->
                builder.individuals(Set.of(individualFactory.create(i -> i.lastName("Smith")))));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_LASTNAME_PARAM + "David");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 3, 10,0,3);
            assertThat(actual.getApplications().size()).isEqualTo(3);
            assertThat((actual.getApplications()).containsAll(expectedApplications));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByLastNameAndStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            persistedApplicationFactory.createAndPersistMultiple(1, builder ->
                builder
                    .status(ApplicationStatus.IN_PROGRESS)
                    .individuals(Set.of(individualFactory.create(i -> i.lastName("David")))));
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(7, builder ->
                builder
                    .status(ApplicationStatus.SUBMITTED)
                    .individuals(Set.of(individualFactory.create(i -> i.lastName("David")))));
            persistedApplicationFactory.createAndPersistMultiple(5, builder ->
                builder
                    .status(ApplicationStatus.IN_PROGRESS)
                    .individuals(Set.of(individualFactory.create(i -> i.lastName("Smith")))));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                    + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.SUBMITTED
                    + "&" + SEARCH_LASTNAME_PARAM + "David");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 7, 10,0,7);
            assertThat(actual.getApplications().size()).isEqualTo(7);
            assertThat((actual.getApplications()).containsAll(expectedApplications));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByFirstNameAndLastName_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                builder.individuals(Set.of(individualFactory.create(i -> i.firstName("George").lastName("Taylor")))));
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(2, builder ->
                builder.individuals(Set.of(individualFactory.create(i -> i.firstName("Lucas").lastName("Taylor")))));
            persistedApplicationFactory.createAndPersistMultiple(5, builder ->
                builder.individuals(Set.of(individualFactory.create(i -> i.firstName("Victoria").lastName("Williams")))));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                    + "?" + SEARCH_FIRSTNAME_PARAM + "Lucas"
                    + "&" + SEARCH_LASTNAME_PARAM + "Taylor");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 2, 10,0,2);
            assertThat(actual.getApplications().size()).isEqualTo(2);
            assertThat((actual.getApplications()).containsAll(expectedApplications));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByFirstNameAndLastNameAndStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(1, builder ->
                    builder
                        .status(ApplicationStatus.IN_PROGRESS)
                        .individuals(Set.of(individualFactory.create(i -> i.firstName("George").lastName("Theodore")))));
            persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                    builder
                        .status(ApplicationStatus.SUBMITTED)
                        .individuals(Set.of(individualFactory.create(i -> i.firstName("George").lastName("Theodore")))));
            persistedApplicationFactory.createAndPersistMultiple(2, builder ->
                    builder.individuals(Set.of(individualFactory.create(i -> i.firstName("Lucas").lastName("Jones")))));
            persistedApplicationFactory.createAndPersistMultiple(5, builder ->
                    builder.individuals(Set.of(individualFactory.create(i -> i.firstName("Victoria").lastName("Theodore")))));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                    + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.IN_PROGRESS
                    + "&" + SEARCH_FIRSTNAME_PARAM + "George"
                    + "&" + SEARCH_LASTNAME_PARAM + "Theodore");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 1, 10,0,1);
            assertThat(actual.getApplications().size()).isEqualTo(1);
            assertThat((actual.getApplications()).containsAll(expectedApplications));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByFirstNameAndLastNameAndStatusWithPaging_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
            // given
            List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(13, builder ->
                    builder
                        .status(ApplicationStatus.IN_PROGRESS)
                        .individuals(Set.of(individualFactory.create(i -> i.firstName("George").lastName("Theodore")))));
            persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                    builder
                        .status(ApplicationStatus.SUBMITTED)
                        .individuals(Set.of(individualFactory.create(i -> i.firstName("George").lastName("Theodore")))));
            persistedApplicationFactory.createAndPersistMultiple(2, builder ->
                    builder.individuals(Set.of(individualFactory.create(i -> i.firstName("Lucas").lastName("Jones")))));
            persistedApplicationFactory.createAndPersistMultiple(5, builder ->
                    builder.individuals(Set.of(individualFactory.create(i -> i.firstName("Victoria").lastName("Theodore")))));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                    + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.IN_PROGRESS
                    + "&" + SEARCH_FIRSTNAME_PARAM + "George"
                    + "&" + SEARCH_LASTNAME_PARAM + "Theodore"
                    + "&" + SEARCH_PAGE_PARAM + "1");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 13, 10,1,3);
            assertThat(actual.getApplications().size()).isEqualTo(3);
            assertThat((actual.getApplications()).containsAll(expectedApplications.subList(10,12)));
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.READER)
        void givenApplicationsFilteredByStatusAndNoApplicationsMatch_whenGetApplications_thenReturnEmptyResult() throws Exception {
            // given
            persistedApplicationFactory.createAndPersistMultiple(7, builder ->
                builder
                    .status(ApplicationStatus.IN_PROGRESS)
                    .individuals(Set.of(individualFactory.create(i -> i.firstName("George").lastName("Theodore")))));
            persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                builder
                    .status(ApplicationStatus.SUBMITTED)
                    .individuals(Set.of(individualFactory.create(i -> i.firstName("George").lastName("Theodore")))));
            persistedApplicationFactory.createAndPersistMultiple(2, builder ->
                builder
                    .status(ApplicationStatus.SUBMITTED)
                    .individuals(Set.of(individualFactory.create(i -> i.firstName("Lucas").lastName("Jones")))));
            persistedApplicationFactory.createAndPersistMultiple(5, builder ->
                builder.individuals(Set.of(individualFactory.create(i -> i.firstName("Victoria").lastName("Theodore")))));

            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                    + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.IN_PROGRESS
                    + "&" + SEARCH_FIRSTNAME_PARAM + "Lucas"
                    + "&" + SEARCH_LASTNAME_PARAM + "Jones");
            ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

            // then
            assertContentHeaders(result);
            assertSecurityHeaders(result);
            assertNoCacheHeaders(result);
            assertOK(result);
            assertPaging(actual, 0, 10,0,0);
            assertThat(actual.getApplications().size()).isEqualTo(0);
        }

        @Test
        public void givenNoUser_whenGetApplications_thenReturnUnauthorised() throws Exception {
            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);

            // then
            assertSecurityHeaders(result);
            assertUnauthorised(result);
        }

        @Test
        @WithMockUser(authorities = TestConstants.Roles.UNKNOWN)
        public void givenNoRole_wwhenGetApplications_thenReturnForbidden() throws Exception {
            // when
            MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);

            // then
            assertSecurityHeaders(result);
            assertForbidden(result);
        }

        private void assertPaging(
                ApplicationSummaryResponse applicationSummaryResponse,
                Integer totalRecords,
                Integer pageSize,
                Integer page,
                Integer itemsReturned
                ) {
            assertThat(applicationSummaryResponse.getPaging().getTotalRecords()).isEqualTo(totalRecords);
            assertThat(applicationSummaryResponse.getPaging().getPageSize()).isEqualTo(pageSize);
            assertThat(applicationSummaryResponse.getPaging().getPage()).isEqualTo(page);
            assertThat(applicationSummaryResponse.getPaging().getItemsReturned()).isEqualTo(itemsReturned);
        }
    }
}
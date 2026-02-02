package uk.gov.justice.laa.dstew.access.controller.application;

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
import uk.gov.justice.laa.dstew.access.model.*;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertBadRequest;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertContentHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GetApplicationsTest extends BaseIntegrationTest {

    public static final String SEARCH_PAGE_PARAM = "page=";
    public static final String SEARCH_PAGE_SIZE_PARAM = "pageSize=";
    public static final String SEARCH_STATUS_PARAM = "status=";
    public static final String SEARCH_FIRSTNAME_PARAM = "clientFirstName=";
    public static final String SEARCH_LASTNAME_PARAM = "clientLastName=";
    public static final String SEARCH_CASEWORKERID_PARAM = "userId=";
    public static final String SEARCH_ISAUTOGRANTED_PARAM = "isAutoGranted=";
    public static final String SEARCH_CLIENTDOB_PARAM = "clientDateOfBirth=";
    public static final String SEARCH_MATTERTYPE_PARAM = "matterType=";
    public static final String SEARCH_SORTBY_PARAM = "sortBy=";
    public static final String SEARCH_ORDERBY_PARAM = "orderBy=";

    private static Stream<Arguments> something() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of("?" + SEARCH_ORDERBY_PARAM + "ASC"),
                Arguments.of("?" + SEARCH_ORDERBY_PARAM + "DESC")
        );
    }

    @ParameterizedTest
    @WithMockUser(authorities = TestConstants.Roles.READER)
    @MethodSource("something")
    void givenApplicationWithoutFilteringAndOrderedBy_whenGetApplications_thenReturnApplication(
            String parameterQuery
    ) throws Exception {

        boolean sortDescending = parameterQuery.endsWith("DESC");

        List<ApplicationEntity> expectedApplications =
                sortApplications(sortDescending, createRangeOfSortableApplications());

        persistedApplicationFactory.persistMultiple(expectedApplications);

        getAndConfirmSortedApplications(
                TestConstants.URIs.GET_APPLICATIONS + parameterQuery,
                expectedApplications);
    }

    private List<ApplicationEntity> createRangeOfSortableApplications() {
        List<ApplicationEntity> expectedApplications = persistedApplicationFactory
                .createMultiple(3, builder ->
                        builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS));

        int lastSubmittedDayCount = 0;
        Instant referenceDate = Instant.now();

        for (ApplicationEntity applicationEntity : expectedApplications) {
            applicationEntity.setSubmittedAt(referenceDate.plus(lastSubmittedDayCount++, ChronoUnit.DAYS));
        }

        return expectedApplications;
    }

private List<ApplicationEntity> sortApplications(boolean orderDescending,
                                                 List<ApplicationEntity> applications) {
    return applications.stream()
      .sorted((a1, a2) -> {
            if (orderDescending) {
                return a2.getSubmittedAt().compareTo(a1.getSubmittedAt());
            }
          return a1.getSubmittedAt().compareTo(a2.getSubmittedAt());
        }
      )
      .toList();
    }

    private void getAndConfirmSortedApplications(String uri,
                                        List<ApplicationEntity> expectedApplications) throws Exception {

        MvcResult result = getUri(uri);
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);
        assertOK(result);
        List<ApplicationSummary> actualApplications = actual.getApplications();
        assertThat(actualApplications.size()).isEqualTo(expectedApplications.size());
        assertEquals(actualApplications.getFirst().getApplicationId(), expectedApplications.getFirst().getId());
        assertEquals(actualApplications.getLast().getApplicationId(), expectedApplications.getLast().getId());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationWithoutFilteringAndNullAutoGranted_whenGetApplications_thenReturnApplication() throws Exception {
        // given
        List<ApplicationEntity> expectedApplicationsWithNullAutoGrant =
                persistedApplicationFactory.createAndPersistMultiple(1, builder ->
                builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS));

        expectedApplicationsWithNullAutoGrant.getFirst().setIsAutoGranted(null);

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        // then
        assertNull(actual.getApplications().getFirst().getAutoGrant());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationsWithoutFiltering_whenGetApplications_thenReturnApplicationsWithPagingCorrectly() throws Exception {
        // given
        List<ApplicationEntity> expectedApplicationsWithCaseworker = persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS));
        List<ApplicationEntity> expectedApplicationWithDifferentCaseworker = persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS).caseworker(CaseworkerJaneDoe));
        List<ApplicationEntity> expectedApplicationWithNoCaseworker = persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS).caseworker(null));

        List<ApplicationSummary> expectedApplicationsSummary = Stream.of(
                        expectedApplicationsWithCaseworker,
                        expectedApplicationWithDifferentCaseworker,
                        expectedApplicationWithNoCaseworker
                )
                .flatMap(List::stream)
                .map(this::createApplicationSummary)
                .toList();

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, 9, 10, 1, 9);
        assertThat(actual.getApplications().size()).isEqualTo(9);
        assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationsRequiringPageTwo_whenGetApplications_thenReturnSecondPageOfApplicationsCorrectly() throws Exception {
        // given
        List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory.createAndPersistMultiple(20, builder ->
                        builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS))
                .stream()
                .map(this::createApplicationSummary)
                .toList();

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_PAGE_PARAM + "2");
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, 20, 10, 2, 10);
        assertThat(actual.getApplications().size()).isEqualTo(10);
        assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(10, 20)));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationsAndPageSizeOfTwenty_whenGetApplications_thenReturnTwentyRecords() throws Exception {
        // given
        List<ApplicationEntity> inProgressApplications = persistedApplicationFactory.createAndPersistMultiple(15, builder -> builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS));
        List<ApplicationEntity> submittedApplications = persistedApplicationFactory.createAndPersistMultiple(10, builder -> builder.status(ApplicationStatus.APPLICATION_SUBMITTED));

        List<ApplicationSummary> expectedApplicationsSummary = Stream.concat(
                        inProgressApplications.stream(),
                        submittedApplications.stream().limit(5)
                )
                .map(this::createApplicationSummary)
                .toList();

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_PAGE_SIZE_PARAM + "20");
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, 25, 20, 1, 20);
        assertThat(actual.getApplications().size()).isEqualTo(20);
        assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @ParameterizedTest
    @MethodSource("applicationsSummaryFilteredByStatusCases")
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationsFilteredByStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly(
            ApplicationStatus Status,
            Supplier<List<ApplicationSummary>> expectedApplicationsSummarySupplier,
            int numberOfApplications
    ) throws Exception {
        // given
        List<ApplicationSummary> expectedApplicationsSummary = expectedApplicationsSummarySupplier.get();

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_STATUS_PARAM + Status);
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, numberOfApplications, 10, 1, numberOfApplications);
        assertThat(actual.getApplications().size()).isEqualTo(numberOfApplications);
        assertTrue((actual.getApplications()).containsAll(expectedApplicationsSummary));
    }

    // TODO: is this test superseded by parameterized test above?
    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationsFilteredByInProgressStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
        // given
        List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory
                .createAndPersistMultiple(5, builder -> builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS))
                .stream()
                .map(this::createApplicationSummary)
                .toList();

        persistedApplicationFactory.createAndPersistMultiple(10, builder -> builder.status(ApplicationStatus.APPLICATION_SUBMITTED));

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.APPLICATION_IN_PROGRESS);
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, 5, 10, 1, 5);
        assertThat(actual.getApplications().size()).isEqualTo(5);
        assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    // TODO: is this test superseded by parameterized test above?
    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationsFilteredBySubmittedStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
        // given
        List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory
                .createAndPersistMultiple(6, builder -> builder.status(ApplicationStatus.APPLICATION_SUBMITTED))
                .stream()
                .map(this::createApplicationSummary)
                .toList();
        persistedApplicationFactory.createAndPersistMultiple(10, builder -> builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS));

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.APPLICATION_SUBMITTED);
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, 6, 10, 1, 6);
        assertThat(actual.getApplications().size()).isEqualTo(6);
        assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationsFilteredBySubmittedStatusWithPaging_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
        // given
        List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory
                .createAndPersistMultiple(17, builder -> builder.status(ApplicationStatus.APPLICATION_SUBMITTED))
                .stream()
                .map(this::createApplicationSummary)
                .toList();
        persistedApplicationFactory.createAndPersistMultiple(10, builder -> builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS));

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.APPLICATION_SUBMITTED
                + "&" + SEARCH_PAGE_PARAM + "2");
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, 17, 10, 2, 7);
        assertThat(actual.getApplications().size()).isEqualTo(7);
        assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(10, 17)));
    }

    @ParameterizedTest
    @MethodSource("firstNameSearchCases")
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationsFilteredByFirstName_whenGetApplications_thenReturnExpectedApplicationsCorrectly(
            String searchFirstName,
            String persistedFirstName,
            int expectedCount
    ) throws Exception {
        // given
        persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                builder.individuals(Set.of(individualEntityFactory.create(i -> i.firstName("John")))));

        List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory.createAndPersistMultiple(expectedCount, builder ->
                        builder.individuals(Set.of(individualEntityFactory.create(i -> i.firstName(persistedFirstName)))))
                .stream()
                .map(this::createApplicationSummary)
                .toList();

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_FIRSTNAME_PARAM + searchFirstName);
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, expectedCount, 10, 1, expectedCount);
        assertThat(actual.getApplications().size()).isEqualTo(expectedCount);
        assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationsFilteredByFirstNameAndStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
        // given
        persistedApplicationFactory.createAndPersistMultiple(8, builder ->
                builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                        .individuals(Set.of(individualEntityFactory.create(i -> i.firstName("Jane")))));

        List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory.createAndPersistMultiple(7, builder ->
                        builder.status(ApplicationStatus.APPLICATION_SUBMITTED)
                                .individuals(Set.of(individualEntityFactory.create(i -> i.firstName("Jane")))))
                .stream()
                .map(this::createApplicationSummary)
                .toList();

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.APPLICATION_SUBMITTED
                + "&" + SEARCH_FIRSTNAME_PARAM + "Jane");
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, 7, 10, 1, 7);
        assertThat(actual.getApplications().size()).isEqualTo(7);
        assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @ParameterizedTest
    @MethodSource("lastNameSearchCases")
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationsFilteredByLastName_whenGetApplications_thenReturnExpectedApplicationsCorrectly(
            String searchLastName,
            String persistedLastName,
            int expectedCount
    ) throws Exception {
        // given
        persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                builder.individuals(Set.of(individualEntityFactory.create(i -> i.lastName("Johnson")))));
        List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory.createAndPersistMultiple(expectedCount, builder ->
                        builder.individuals(Set.of(individualEntityFactory.create(i -> i.lastName(persistedLastName)))))
                .stream()
                .map(this::createApplicationSummary)
                .toList();

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_LASTNAME_PARAM + searchLastName);
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, expectedCount, 10, 1, expectedCount);
        assertThat(actual.getApplications().size()).isEqualTo(expectedCount);
        assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationsFilteredByLastNameAndStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
        // given
        persistedApplicationFactory.createAndPersistMultiple(1, builder ->
                builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                        .individuals(Set.of(individualEntityFactory.create(i -> i.lastName("David")))));

        List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(7, builder ->
                builder.status(ApplicationStatus.APPLICATION_SUBMITTED)
                        .individuals(Set.of(individualEntityFactory.create(i -> i.lastName("David")))));

        persistedApplicationFactory.createAndPersistMultiple(5, builder ->
                builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                        .individuals(Set.of(individualEntityFactory.create(i -> i.lastName("Smith")))));

        List<ApplicationSummary> expectedApplicationsSummary = expectedApplications.stream()
                .map(this::createApplicationSummary)
                .toList();

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.APPLICATION_SUBMITTED
                + "&" + SEARCH_LASTNAME_PARAM + "David");
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, 7, 10, 1, 7);
        assertThat(actual.getApplications().size()).isEqualTo(7);
        assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationsFilteredByFirstNameAndLastName_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
        // given
        persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                builder.individuals(Set.of(individualEntityFactory.create(i -> i.firstName("George").lastName("Taylor")))));
        List<ApplicationSummary> expectedApplicationsSummary = persistedApplicationFactory.createAndPersistMultiple(2, builder ->
                        builder.individuals(Set.of(individualEntityFactory.create(i -> i.firstName("Lucas").lastName("Taylor")))))
                .stream()
                .map(this::createApplicationSummary)
                .toList();
        persistedApplicationFactory.createAndPersistMultiple(5, builder ->
                builder.individuals(Set.of(individualEntityFactory.create(i -> i.firstName("Victoria").lastName("Williams")))));

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
        assertPaging(actual, 2, 10, 1, 2);
        assertThat(actual.getApplications().size()).isEqualTo(2);
        assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationsFilteredByFirstNameAndLastNameAndStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
        // given
        List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(1, builder ->
                builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                        .individuals(Set.of(individualEntityFactory.create(i -> i.firstName("George").lastName("Theodore")))));

        persistedApplicationFactory.createAndPersistMultiple(3, builder -> builder.status(ApplicationStatus.APPLICATION_SUBMITTED)
                .individuals(Set.of(individualEntityFactory.create(i -> i.firstName("George").lastName("Theodore")))));
        persistedApplicationFactory.createAndPersistMultiple(2, builder -> builder.individuals(Set.of(
                individualEntityFactory.create(i -> i.firstName("Lucas").lastName("Jones")))));
        persistedApplicationFactory.createAndPersistMultiple(5, builder -> builder.individuals(Set.of(
                individualEntityFactory.create(i -> i.firstName("Victoria").lastName("Theodore")))));

        List<ApplicationSummary> expectedApplicationsSummary = expectedApplications.stream()
                .map(this::createApplicationSummary)
                .toList();

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.APPLICATION_IN_PROGRESS
                + "&" + SEARCH_FIRSTNAME_PARAM + "George"
                + "&" + SEARCH_LASTNAME_PARAM + "Theodore");
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, 1, 10, 1, 1);
        assertThat(actual.getApplications().size()).isEqualTo(1);
        assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationsFilteredByFirstNameAndLastNameAndStatusWithPaging_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
        // given
        List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(13, builder ->
                builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                        .individuals(Set.of(individualEntityFactory.create(i -> i.firstName("George").lastName("Theodore")))));

        persistedApplicationFactory.createAndPersistMultiple(3, builder -> builder.status(ApplicationStatus.APPLICATION_SUBMITTED)
                .individuals(Set.of(individualEntityFactory.create(i -> i.firstName("George").lastName("Theodore")))));
        persistedApplicationFactory.createAndPersistMultiple(2, builder -> builder.individuals(Set.of(
                individualEntityFactory.create(i -> i.firstName("Lucas").lastName("Jones")))));
        persistedApplicationFactory.createAndPersistMultiple(5, builder -> builder.individuals(Set.of(
                individualEntityFactory.create(i -> i.firstName("Victoria").lastName("Theodore")))));

        List<ApplicationSummary> expectedApplicationsSummary = expectedApplications.stream()
                .map(this::createApplicationSummary)
                .toList();

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.APPLICATION_IN_PROGRESS
                + "&" + SEARCH_FIRSTNAME_PARAM + "George"
                + "&" + SEARCH_LASTNAME_PARAM + "Theodore"
                + "&" + SEARCH_PAGE_PARAM + "2");
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, 13, 10, 2, 3);
        assertThat(actual.getApplications().size()).isEqualTo(3);
        assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(10, 13)));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationsFilteredByClientDateOfBirth_whenGetAllApplications_thenReturnExpectedApplication() throws Exception {
        //given
        LocalDate clientDOB = LocalDate.of(1942, 11, 27);
        List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(2, builder ->
                builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                        .individuals(Set.of(individualEntityFactory.create(i -> i.firstName("Jimi").lastName("Hendrix").dateOfBirth(clientDOB)))));
        var expectedApplicationSummary = expectedApplications.stream().map(this::createApplicationSummary).toList();
        persistedApplicationFactory.createAndPersist();

        //when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                + "?" + SEARCH_CLIENTDOB_PARAM + "1942-11-27");
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        //then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, 2, 10, 1, 2);
        assertThat(actual.getApplications().size()).isEqualTo(2);
        assertArrayEquals(actual.getApplications().toArray(), expectedApplicationSummary.toArray());
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationFilteredByClientDateOfBirth_whenGetAllApplicationsAndInvalidFormat_thenReturnBadRequest() throws Exception {
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_CLIENTDOB_PARAM + "something");
        assertBadRequest(result);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    void givenApplicationsFilteredByStatusAndNoApplicationsMatch_whenGetApplications_thenReturnEmptyResult() throws Exception {
        // given
        persistedApplicationFactory.createAndPersistMultiple(7, builder ->
                builder
                        .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                        .individuals(Set.of(individualEntityFactory.create(i -> i.firstName("George").lastName("Theodore")))));
        persistedApplicationFactory.createAndPersistMultiple(3, builder ->
                builder
                        .status(ApplicationStatus.APPLICATION_SUBMITTED)
                        .individuals(Set.of(individualEntityFactory.create(i -> i.firstName("George").lastName("Theodore")))));
        persistedApplicationFactory.createAndPersistMultiple(2, builder ->
                builder
                        .status(ApplicationStatus.APPLICATION_SUBMITTED)
                        .individuals(Set.of(individualEntityFactory.create(i -> i.firstName("Lucas").lastName("Jones")))));
        persistedApplicationFactory.createAndPersistMultiple(5, builder ->
                builder.individuals(Set.of(individualEntityFactory.create(i -> i.firstName("Victoria").lastName("Theodore")))));

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS
                + "?" + SEARCH_STATUS_PARAM + ApplicationStatus.APPLICATION_IN_PROGRESS
                + "&" + SEARCH_FIRSTNAME_PARAM + "Lucas"
                + "&" + SEARCH_LASTNAME_PARAM + "Jones");
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, 0, 10, 1, 0);
        assertThat(actual.getApplications().size()).isEqualTo(0);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenApplicationsFilteredByCaseworkerJohnDoe_whenGetApplications_thenReturnExpectedApplicationsCorrectly() throws Exception {
        // given
        List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(4, builder ->
                builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe));

        persistedApplicationFactory.createAndPersistMultiple(6, builder ->
                builder.caseworker(BaseIntegrationTest.CaseworkerJaneDoe));

        List<ApplicationSummary> expectedApplicationsSummary = expectedApplications.stream()
                .map(this::createApplicationSummary)
                .toList();

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_CASEWORKERID_PARAM + BaseIntegrationTest.CaseworkerJohnDoe.getId());
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, 4, 10, 1, 4);
        assertThat(actual.getApplications().size()).isEqualTo(4);
        assertArrayEquals(expectedApplicationsSummary.toArray(), actual.getApplications().toArray());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenApplicationFilteredByAutoGrant_whenGetApplications_thenReturnExpectedApplicationsCorrectly(boolean isAutoGranted) throws Exception {
        // given
        List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(4, builder ->
                builder.isAutoGranted(isAutoGranted));
        persistedApplicationFactory.createAndPersistMultiple(6, builder ->
                builder.isAutoGranted(!isAutoGranted));
        List<ApplicationSummary> expectedApplicationsSummary = expectedApplications.stream()
                .map(this::createApplicationSummary)
                .toList();

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_ISAUTOGRANTED_PARAM + isAutoGranted);
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);
        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, 4, 10, 1, 4);
        assertThat(actual.getApplications().size()).isEqualTo(4);
        assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenApplicationFilteredByAutoGrant_whenGetApplicationsAndInvalidFormat_thenReturnBadRequest() throws Exception {
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_ISAUTOGRANTED_PARAM + "something");
        assertBadRequest(result);
    }

    private Stream<Arguments> getApplicationSummaryQueryMatterTypes() {
        return Stream.of(Arguments.of(MatterType.SCA));
    }

    @ParameterizedTest
    @MethodSource("getApplicationSummaryQueryMatterTypes")
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenApplicationFilteredByMatterType_whenGetApplications_thenReturnExpectedApplicationsCorrectly(MatterType matterType) throws Exception {
        // given
        List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(4, builder ->
                builder.matterType(matterType));

        List<ApplicationSummary> expectedApplicationsSummary = expectedApplications.stream()
                .map(this::createApplicationSummary)
                .toList();

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_MATTERTYPE_PARAM + matterType);
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);
        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, 4, 10, 1, 4);
        assertThat(actual.getApplications().size()).isEqualTo(4);
        assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenApplicationFilteredByMatterType_whenGetApplicationsAndInvalidFormat_thenReturnBadRequest() throws Exception {
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_MATTERTYPE_PARAM + "something");
        assertBadRequest(result);
    }

    @Test
    @WithMockUser(authorities = TestConstants.Roles.READER)
    public void givenPageZero_whenGetApplications_thenDefaultToPageOneAndReturnCorrectResults() throws Exception {
        // given
        List<ApplicationEntity> expectedApplications = persistedApplicationFactory.createAndPersistMultiple(15, builder ->
                builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS));

        List<ApplicationSummary> expectedApplicationsSummary = expectedApplications.stream()
                .map(this::createApplicationSummary)
                .toList();

        // when
        MvcResult result = getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_PAGE_PARAM + "0");
        ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

        // then
        assertContentHeaders(result);
        assertSecurityHeaders(result);
        assertNoCacheHeaders(result);
        assertOK(result);
        assertPaging(actual, 15, 10, 1, 10);
        assertThat(actual.getApplications().size()).isEqualTo(10);
        assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(0, 10)));
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
    public void givenNoRole_whenGetApplications_thenReturnForbidden() throws Exception {
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

    private static Stream<Arguments> firstNameSearchCases() {
        return Stream.of(
                Arguments.of("Jane", "Jane", 5),
                Arguments.of("Jan", "Jane", 3),
                Arguments.of("ne", "Jane", 7),
                Arguments.of("an", "Jane", 4),
                Arguments.of("ANE", "Jane", 6)
        );
    }

    private static Stream<Arguments> lastNameSearchCases() {
        return Stream.of(
                Arguments.of("Smith", "Smith", 3),
                Arguments.of("Smi", "Smith", 7),
                Arguments.of("ith", "Smith", 5),
                Arguments.of("mit", "Smith", 8),
                Arguments.of("MITH", "Smith", 4)
        );
    }

    private Stream<Arguments> applicationsSummaryFilteredByStatusCases() {
        return Stream.of(
                Arguments.of(ApplicationStatus.APPLICATION_IN_PROGRESS, (Supplier<List<ApplicationSummary>>) () -> generateApplicationSummaries(ApplicationStatus.APPLICATION_IN_PROGRESS, 8), 8),
                Arguments.of(ApplicationStatus.APPLICATION_SUBMITTED, (Supplier<List<ApplicationSummary>>) () -> generateApplicationSummaries(ApplicationStatus.APPLICATION_SUBMITTED, 5), 5)
        );
    }

    private List<ApplicationSummary> generateApplicationSummaries(ApplicationStatus status, int numberOfApplications) {
        Random random = new Random();

        return persistedApplicationFactory.createAndPersistMultiple(numberOfApplications, builder ->
                        builder.status(status).laaReference("REF-00" + random.nextInt(100)))
                .stream()
                .map(this::createApplicationSummary)
                .collect(Collectors.toList());
    }

    private ApplicationSummary createApplicationSummary(ApplicationEntity applicationEntity) {
        ApplicationSummary applicationSummary = new ApplicationSummary();
        applicationSummary.setApplicationId(applicationEntity.getId());
        applicationSummary.setStatus(applicationEntity.getStatus());
        applicationSummary.setSubmittedAt(applicationEntity.getSubmittedAt().atOffset(ZoneOffset.UTC));
        applicationSummary.setLastUpdated(applicationEntity.getModifiedAt().atOffset(ZoneOffset.UTC));
        applicationSummary.setUsedDelegatedFunctions(applicationEntity.isUseDelegatedFunctions());
        applicationSummary.setCategoryOfLaw(applicationEntity.getCategoryOfLaw());
        applicationSummary.setMatterType(applicationEntity.getMatterType());
        applicationSummary.setAssignedTo(applicationEntity.getCaseworker() != null ? applicationEntity.getCaseworker().getId() : null);
        applicationSummary.autoGrant(applicationEntity.getIsAutoGranted());
        applicationSummary.setLaaReference(applicationEntity.getLaaReference());
        applicationSummary.setApplicationType(ApplicationType.INITIAL);
        applicationSummary.setClientFirstName(applicationEntity.getIndividuals().stream().findFirst().get().getFirstName());
        applicationSummary.setClientLastName(applicationEntity.getIndividuals().stream().findFirst().get().getLastName());
        applicationSummary.setClientDateOfBirth(applicationEntity.getIndividuals().stream().findFirst().get().getDateOfBirth());
        return applicationSummary;
    }
}

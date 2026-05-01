package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertBadRequest;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertContentHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.*;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.harness.BaseHarnessTest;
import uk.gov.justice.laa.dstew.access.utils.harness.HarnessResult;
import uk.gov.justice.laa.dstew.access.utils.harness.SmokeTest;

public class GetApplicationsTest extends BaseHarnessTest {

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
  public static final String SEARCH_SORTBY_SUBMITTED_PARAM = "SUBMITTED_DATE";
  public static final String SEARCH_SORTBY_LAST_UPDATED_PARAM = "LAST_UPDATED_DATE";
  public static final String SEARCH_ORDERBY_ASC_PARAM = "ASC";
  public static final String SEARCH_ORDERBY_DESC_PARAM = "DESC";

  private static Stream<Arguments> searchFieldAndOrderParameters() {
    return Stream.of(
        Arguments.of("", ""),
        Arguments.of("", SEARCH_ORDERBY_ASC_PARAM),
        Arguments.of("", SEARCH_ORDERBY_DESC_PARAM),
        Arguments.of(SEARCH_SORTBY_SUBMITTED_PARAM, ""),
        Arguments.of(SEARCH_SORTBY_SUBMITTED_PARAM, SEARCH_ORDERBY_ASC_PARAM),
        Arguments.of(SEARCH_SORTBY_SUBMITTED_PARAM, SEARCH_ORDERBY_DESC_PARAM),
        Arguments.of(SEARCH_SORTBY_LAST_UPDATED_PARAM, ""),
        Arguments.of(SEARCH_SORTBY_LAST_UPDATED_PARAM, SEARCH_ORDERBY_ASC_PARAM),
        Arguments.of(SEARCH_SORTBY_LAST_UPDATED_PARAM, SEARCH_ORDERBY_DESC_PARAM));
  }

  @ParameterizedTest
  @MethodSource("searchFieldAndOrderParameters")
  void givenApplicationWithoutFilteringAndOrderedBy_whenGetApplications_thenReturnApplication(
      String sortByParameter, String orderByParameter) throws Exception {

    boolean orderByDescending = orderByParameter.endsWith("DESC");
    String sortByField =
        sortByParameter.isEmpty() ? SEARCH_SORTBY_SUBMITTED_PARAM : sortByParameter;

    List<ApplicationEntity> expectedApplications = createRangeOfSortableApplications();
    List<ApplicationEntity> expectedSortedApplications =
        sortApplications(orderByDescending, sortByField, expectedApplications);

    getAndConfirmSortedApplications(
        createUriForSorting(TestConstants.URIs.GET_APPLICATIONS, sortByParameter, orderByParameter),
        expectedSortedApplications);
  }

  @SmokeTest
  @ParameterizedTest
  @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
  void givenValidApplicationsDataAndIncorrectHeader_whenGetApplications_thenReturnBadRequest(
      String serviceName) throws Exception {
    verifyBadServiceNameHeader(serviceName);
  }

  @SmokeTest
  @Test
  void givenValidApplicationsDataAndNoHeader_whenGetApplications_thenReturnBadRequest()
      throws Exception {
    verifyBadServiceNameHeader(null);
  }

  private void verifyBadServiceNameHeader(String serviceName) throws Exception {
    HarnessResult result =
        getUri(
            createUriForSorting(
                TestConstants.URIs.GET_APPLICATIONS,
                SEARCH_SORTBY_SUBMITTED_PARAM,
                SEARCH_ORDERBY_ASC_PARAM),
            ServiceNameHeader(serviceName));
    applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
  }

  private String createUriForSorting(String uri, String sortBy, String orderBy) {
    String orderByQuery = SEARCH_ORDERBY_PARAM + orderBy;
    String sortByQuery = SEARCH_SORTBY_PARAM + sortBy;

    if (!sortBy.isEmpty()) {
      uri += "?" + sortByQuery;
    }

    if (!orderBy.isEmpty()) {
      uri += (sortBy.isEmpty()) ? "?" + orderByQuery : "&" + orderByQuery;
    }

    return uri;
  }

  private List<ApplicationEntity> createRangeOfSortableApplications() {
    List<ApplicationEntity> applications =
        persistedDataGenerator.createAndPersistMultiple(
            ApplicationEntityGenerator.class,
            3,
            builder -> builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS));

    int submittedDayCount = 0;
    Instant referenceDate = Instant.now();

    for (ApplicationEntity applicationEntity : applications) {
      applicationEntity.setSubmittedAt(referenceDate.plus(submittedDayCount++, ChronoUnit.DAYS));
      persistedDataGenerator.updateAndFlush(applicationEntity);
    }

    return applications;
  }

  private List<ApplicationEntity> sortApplications(
      boolean orderDescending, String fieldToSortBy, List<ApplicationEntity> applications) {
    return applications.stream()
        .sorted(
            (a1, a2) -> {
              if (orderDescending) {
                if (Objects.equals(fieldToSortBy, SEARCH_SORTBY_SUBMITTED_PARAM)) {
                  return a2.getSubmittedAt().compareTo(a1.getSubmittedAt());
                }
                return a2.getModifiedAt().compareTo(a1.getModifiedAt());
              }

              if (Objects.equals(fieldToSortBy, SEARCH_SORTBY_SUBMITTED_PARAM)) {
                return a1.getSubmittedAt().compareTo(a2.getSubmittedAt());
              }
              return a1.getModifiedAt().compareTo(a2.getModifiedAt());
            })
        .toList();
  }

  private void getAndConfirmSortedApplications(
      String uri, List<ApplicationEntity> expectedApplications) throws Exception {
    HarnessResult result = getUri(uri);
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);
    assertOK(result);
    List<ApplicationSummary> actualApplications = actual.getApplications();
    assertThat(actualApplications.size()).isEqualTo(expectedApplications.size());
    assertEquals(
        actualApplications.getFirst().getApplicationId(), expectedApplications.getFirst().getId());
    assertEquals(
        actualApplications.getLast().getApplicationId(), expectedApplications.getLast().getId());
  }

  @Test
  void
      givenApplicationWithoutFilteringAndNullAutoGranted_whenGetApplications_thenReturnApplication()
          throws Exception {
    // given
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        1,
        builder -> builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS).isAutoGranted(null));

    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertNull(actual.getApplications().getFirst().getAutoGrant());
  }

  @Test
  void
      givenApplicationsFilteredByFirstNameAndStatus_MultipleIndividuals_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
          throws Exception {
    // given

    uk.gov.justice.laa.dstew.access.entity.IndividualEntity jane =
        DataGenerator.createDefault(IndividualEntityGenerator.class, i -> i.firstName("Jane"));
    uk.gov.justice.laa.dstew.access.entity.IndividualEntity alice =
        DataGenerator.createDefault(IndividualEntityGenerator.class, i -> i.firstName("Alice"));
    persistedDataGenerator.createAndPersist(
        ApplicationEntityGenerator.class,
        builder ->
            builder
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .individuals(Set.of(jane, alice)));

    // when
    HarnessResult result =
        getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_FIRSTNAME_PARAM + "Alice");
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertThat(actual.getApplications().size()).isEqualTo(1);
    assertEquals("Jane", actual.getApplications().getFirst().getClientFirstName());
  }

  @Test
  void
      givenApplicationsWithoutFiltering_whenGetApplications_thenReturnApplicationsWithPagingCorrectly()
          throws Exception {
    // given
    List<ApplicationEntity> expectedApplicationsWithCaseworker =
        persistedDataGenerator.createAndPersistMultiple(
            ApplicationEntityGenerator.class,
            3,
            builder -> builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS));
    List<ApplicationEntity> expectedApplicationWithDifferentCaseworker =
        persistedDataGenerator.createAndPersistMultiple(
            ApplicationEntityGenerator.class,
            3,
            builder ->
                builder
                    .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                    .caseworker(CaseworkerJaneDoe));
    List<ApplicationEntity> expectedApplicationWithNoCaseworker =
        persistedDataGenerator.createAndPersistMultiple(
            ApplicationEntityGenerator.class,
            3,
            builder -> builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS).caseworker(null));

    List<ApplicationSummary> expectedApplicationsSummary =
        Stream.of(
                expectedApplicationsWithCaseworker,
                expectedApplicationWithDifferentCaseworker,
                expectedApplicationWithNoCaseworker)
            .flatMap(List::stream)
            .map(this::createApplicationSummary)
            .toList();

    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 9, 20, 1, 9);
    assertThat(actual.getApplications().size()).isEqualTo(9);
    assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
  }

  @Test
  void
      givenApplicationsRequiringPageTwo_whenGetApplications_thenReturnSecondPageOfApplicationsCorrectly()
          throws Exception {
    // given
    List<ApplicationSummary> expectedApplicationsSummary =
        persistedDataGenerator
            .createAndPersistMultiple(
                ApplicationEntityGenerator.class,
                40,
                builder -> builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS))
            .stream()
            .map(this::createApplicationSummary)
            .toList();

    // when
    HarnessResult result =
        getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_PAGE_PARAM + "2");
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 40, 20, 2, 20);
    assertThat(actual.getApplications().size()).isEqualTo(20);
    assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(20, 40)));
  }

  @Test
  void givenApplicationsAndPageSizeOfTwenty_whenGetApplications_thenReturnTwentyRecords()
      throws Exception {
    // given
    List<ApplicationEntity> inProgressApplications =
        persistedDataGenerator.createAndPersistMultiple(
            ApplicationEntityGenerator.class,
            15,
            builder -> builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS));
    List<ApplicationEntity> submittedApplications =
        persistedDataGenerator.createAndPersistMultiple(
            ApplicationEntityGenerator.class,
            10,
            builder -> builder.status(ApplicationStatus.APPLICATION_SUBMITTED));

    List<ApplicationSummary> expectedApplicationsSummary =
        Stream.concat(inProgressApplications.stream(), submittedApplications.stream().limit(5))
            .map(this::createApplicationSummary)
            .toList();

    // when
    HarnessResult result =
        getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_PAGE_SIZE_PARAM + "20");
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
  @MethodSource("invalidPagingParameters")
  void givenInvalidPagingParameters_whenGetApplications_thenReturnBadRequest(
      Integer page, Integer pageSize) throws Exception {
    String uri = TestConstants.URIs.GET_APPLICATIONS + "?";
    if (page != null) {
      uri += SEARCH_PAGE_PARAM + page;
    }
    if (pageSize != null) {
      uri += (page != null ? "&" : "") + SEARCH_PAGE_SIZE_PARAM + pageSize;
    }
    HarnessResult result = getUri(uri);
    assertBadRequest(result);
  }

  static Stream<Arguments> invalidPagingParameters() {
    return Stream.of(
        Arguments.of(0, 10),
        Arguments.of(-1, 10),
        Arguments.of(1, 0),
        Arguments.of(1, -74),
        Arguments.of(1, 200),
        Arguments.of(0, 0));
  }

  // NOTE:
  // givenApplicationsFilteredByStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly
  // was replaced by _v2 below — the old version used a non-static @MethodSource (incompatible with
  // default PER_METHOD lifecycle); _v2 uses a static source and persists data inside the test body.

  // TODO: is this test superseded by _v2 above?
  @Test
  void
      givenApplicationsFilteredByInProgressStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
          throws Exception {
    // given
    List<ApplicationSummary> expectedApplicationsSummary =
        persistedDataGenerator
            .createAndPersistMultiple(
                ApplicationEntityGenerator.class,
                5,
                builder -> builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS))
            .stream()
            .map(this::createApplicationSummary)
            .toList();

    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        10,
        builder -> builder.status(ApplicationStatus.APPLICATION_SUBMITTED));

    // when
    HarnessResult result =
        getUri(
            TestConstants.URIs.GET_APPLICATIONS
                + "?"
                + SEARCH_STATUS_PARAM
                + ApplicationStatus.APPLICATION_IN_PROGRESS);
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 5, 20, 1, 5);
    assertThat(actual.getApplications().size()).isEqualTo(5);
    assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
  }

  // TODO: is this test superseded by parameterized test above?
  @Test
  void
      givenApplicationsFilteredBySubmittedStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
          throws Exception {
    // given
    List<ApplicationSummary> expectedApplicationsSummary =
        persistedDataGenerator
            .createAndPersistMultiple(
                ApplicationEntityGenerator.class,
                6,
                builder -> builder.status(ApplicationStatus.APPLICATION_SUBMITTED))
            .stream()
            .map(this::createApplicationSummary)
            .toList();
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        10,
        builder -> builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS));

    // when
    HarnessResult result =
        getUri(
            TestConstants.URIs.GET_APPLICATIONS
                + "?"
                + SEARCH_STATUS_PARAM
                + ApplicationStatus.APPLICATION_SUBMITTED);
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 6, 20, 1, 6);
    assertThat(actual.getApplications().size()).isEqualTo(6);
    assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
  }

  @Test
  void
      givenApplicationsFilteredBySubmittedStatusWithPaging_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
          throws Exception {
    // given
    List<ApplicationSummary> expectedApplicationsSummary =
        persistedDataGenerator
            .createAndPersistMultiple(
                ApplicationEntityGenerator.class,
                27,
                builder -> builder.status(ApplicationStatus.APPLICATION_SUBMITTED))
            .stream()
            .map(this::createApplicationSummary)
            .toList();
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        10,
        builder -> builder.status(ApplicationStatus.APPLICATION_IN_PROGRESS));

    // when
    HarnessResult result =
        getUri(
            TestConstants.URIs.GET_APPLICATIONS
                + "?"
                + SEARCH_STATUS_PARAM
                + ApplicationStatus.APPLICATION_SUBMITTED
                + "&"
                + SEARCH_PAGE_PARAM
                + "2");
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 27, 20, 2, 7);
    assertThat(actual.getApplications().size()).isEqualTo(7);
    assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(20, 27)));
  }

  @ParameterizedTest
  @MethodSource("firstNameSearchCases")
  void
      givenApplicationsFilteredByFirstName_whenGetApplications_thenReturnExpectedApplicationsCorrectly(
          String searchFirstName, String persistedFirstName, int expectedCount) throws Exception {
    // given
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        3,
        builder ->
            builder.individuals(
                Set.of(
                    DataGenerator.createDefault(
                        IndividualEntityGenerator.class, i -> i.firstName("John")))));

    List<ApplicationSummary> expectedApplicationsSummary =
        persistedDataGenerator
            .createAndPersistMultiple(
                ApplicationEntityGenerator.class,
                expectedCount,
                builder ->
                    builder.individuals(
                        Set.of(
                            DataGenerator.createDefault(
                                IndividualEntityGenerator.class,
                                i -> i.firstName(persistedFirstName)))))
            .stream()
            .map(this::createApplicationSummary)
            .toList();

    // when
    HarnessResult result =
        getUri(
            TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_FIRSTNAME_PARAM + searchFirstName);
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, expectedCount, 20, 1, expectedCount);
    assertThat(actual.getApplications().size()).isEqualTo(expectedCount);
    assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
  }

  @Test
  void
      givenApplicationsFilteredByFirstNameAndStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
          throws Exception {
    // given
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        8,
        builder ->
            builder
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .individuals(
                    Set.of(
                        DataGenerator.createDefault(
                            IndividualEntityGenerator.class, i -> i.firstName("Jane")))));

    List<ApplicationSummary> expectedApplicationsSummary =
        persistedDataGenerator
            .createAndPersistMultiple(
                ApplicationEntityGenerator.class,
                7,
                builder ->
                    builder
                        .status(ApplicationStatus.APPLICATION_SUBMITTED)
                        .individuals(
                            Set.of(
                                DataGenerator.createDefault(
                                    IndividualEntityGenerator.class, i -> i.firstName("Jane")))))
            .stream()
            .map(this::createApplicationSummary)
            .toList();

    // when
    HarnessResult result =
        getUri(
            TestConstants.URIs.GET_APPLICATIONS
                + "?"
                + SEARCH_STATUS_PARAM
                + ApplicationStatus.APPLICATION_SUBMITTED
                + "&"
                + SEARCH_FIRSTNAME_PARAM
                + "Jane");
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 7, 20, 1, 7);
    assertThat(actual.getApplications().size()).isEqualTo(7);
    assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
  }

  @ParameterizedTest
  @MethodSource("lastNameSearchCases")
  void
      givenApplicationsFilteredByLastName_whenGetApplications_thenReturnExpectedApplicationsCorrectly(
          String searchLastName, String persistedLastName, int expectedCount) throws Exception {
    // given
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        3,
        builder ->
            builder.individuals(
                Set.of(
                    DataGenerator.createDefault(
                        IndividualEntityGenerator.class, i -> i.lastName("Johnson")))));
    List<ApplicationSummary> expectedApplicationsSummary =
        persistedDataGenerator
            .createAndPersistMultiple(
                ApplicationEntityGenerator.class,
                expectedCount,
                builder ->
                    builder.individuals(
                        Set.of(
                            DataGenerator.createDefault(
                                IndividualEntityGenerator.class,
                                i -> i.lastName(persistedLastName)))))
            .stream()
            .map(this::createApplicationSummary)
            .toList();

    // when
    HarnessResult result =
        getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_LASTNAME_PARAM + searchLastName);
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, expectedCount, 20, 1, expectedCount);
    assertThat(actual.getApplications().size()).isEqualTo(expectedCount);
    assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
  }

  @Test
  void
      givenApplicationsFilteredByLastNameAndStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
          throws Exception {
    // given
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        1,
        builder ->
            builder
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .individuals(
                    Set.of(
                        DataGenerator.createDefault(
                            IndividualEntityGenerator.class, i -> i.lastName("David")))));

    List<ApplicationEntity> expectedApplications =
        persistedDataGenerator.createAndPersistMultiple(
            ApplicationEntityGenerator.class,
            7,
            builder ->
                builder
                    .status(ApplicationStatus.APPLICATION_SUBMITTED)
                    .individuals(
                        Set.of(
                            DataGenerator.createDefault(
                                IndividualEntityGenerator.class, i -> i.lastName("David")))));

    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        5,
        builder ->
            builder
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .individuals(
                    Set.of(
                        DataGenerator.createDefault(
                            IndividualEntityGenerator.class, i -> i.lastName("Smith")))));

    List<ApplicationSummary> expectedApplicationsSummary =
        expectedApplications.stream().map(this::createApplicationSummary).toList();

    // when
    HarnessResult result =
        getUri(
            TestConstants.URIs.GET_APPLICATIONS
                + "?"
                + SEARCH_STATUS_PARAM
                + ApplicationStatus.APPLICATION_SUBMITTED
                + "&"
                + SEARCH_LASTNAME_PARAM
                + "David");
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 7, 20, 1, 7);
    assertThat(actual.getApplications().size()).isEqualTo(7);
    assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
  }

  @Test
  void
      givenApplicationsFilteredByFirstNameAndLastName_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
          throws Exception {
    // given
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        3,
        builder ->
            builder.individuals(
                Set.of(
                    DataGenerator.createDefault(
                        IndividualEntityGenerator.class,
                        i -> i.firstName("George").lastName("Taylor")))));
    List<ApplicationSummary> expectedApplicationsSummary =
        persistedDataGenerator
            .createAndPersistMultiple(
                ApplicationEntityGenerator.class,
                2,
                builder ->
                    builder.individuals(
                        Set.of(
                            DataGenerator.createDefault(
                                IndividualEntityGenerator.class,
                                i -> i.firstName("Lucas").lastName("Taylor")))))
            .stream()
            .map(this::createApplicationSummary)
            .toList();
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        5,
        builder ->
            builder.individuals(
                Set.of(
                    DataGenerator.createDefault(
                        IndividualEntityGenerator.class,
                        i -> i.firstName("Victoria").lastName("Williams")))));

    // when
    HarnessResult result =
        getUri(
            TestConstants.URIs.GET_APPLICATIONS
                + "?"
                + SEARCH_FIRSTNAME_PARAM
                + "Lucas"
                + "&"
                + SEARCH_LASTNAME_PARAM
                + "Taylor");
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 2, 20, 1, 2);
    assertThat(actual.getApplications().size()).isEqualTo(2);
    assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
  }

  @Test
  void
      givenApplicationsFilteredByFirstNameAndLastNameAndStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
          throws Exception {
    // given
    List<ApplicationEntity> expectedApplications =
        persistedDataGenerator.createAndPersistMultiple(
            ApplicationEntityGenerator.class,
            1,
            builder ->
                builder
                    .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                    .individuals(
                        Set.of(
                            DataGenerator.createDefault(
                                IndividualEntityGenerator.class,
                                i -> i.firstName("George").lastName("Theodore")))));

    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        3,
        builder ->
            builder
                .status(ApplicationStatus.APPLICATION_SUBMITTED)
                .individuals(
                    Set.of(
                        DataGenerator.createDefault(
                            IndividualEntityGenerator.class,
                            i -> i.firstName("George").lastName("Theodore")))));
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        2,
        builder ->
            builder.individuals(
                Set.of(
                    DataGenerator.createDefault(
                        IndividualEntityGenerator.class,
                        i -> i.firstName("Lucas").lastName("Jones")))));
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        5,
        builder ->
            builder.individuals(
                Set.of(
                    DataGenerator.createDefault(
                        IndividualEntityGenerator.class,
                        i -> i.firstName("Victoria").lastName("Theodore")))));

    List<ApplicationSummary> expectedApplicationsSummary =
        expectedApplications.stream().map(this::createApplicationSummary).toList();

    // when
    HarnessResult result =
        getUri(
            TestConstants.URIs.GET_APPLICATIONS
                + "?"
                + SEARCH_STATUS_PARAM
                + ApplicationStatus.APPLICATION_IN_PROGRESS
                + "&"
                + SEARCH_FIRSTNAME_PARAM
                + "George"
                + "&"
                + SEARCH_LASTNAME_PARAM
                + "Theodore");
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 1, 20, 1, 1);
    assertThat(actual.getApplications().size()).isEqualTo(1);
    assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
  }

  @Test
  void
      givenApplicationsFilteredByFirstNameAndLastNameAndStatusWithPaging_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
          throws Exception {
    // given
    List<ApplicationEntity> expectedApplications =
        persistedDataGenerator.createAndPersistMultiple(
            ApplicationEntityGenerator.class,
            23,
            builder ->
                builder
                    .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                    .individuals(
                        Set.of(
                            DataGenerator.createDefault(
                                IndividualEntityGenerator.class,
                                i -> i.firstName("George").lastName("Theodore")))));

    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        3,
        builder ->
            builder
                .status(ApplicationStatus.APPLICATION_SUBMITTED)
                .individuals(
                    Set.of(
                        DataGenerator.createDefault(
                            IndividualEntityGenerator.class,
                            i -> i.firstName("George").lastName("Theodore")))));
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        2,
        builder ->
            builder.individuals(
                Set.of(
                    DataGenerator.createDefault(
                        IndividualEntityGenerator.class,
                        i -> i.firstName("Lucas").lastName("Jones")))));
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        5,
        builder ->
            builder.individuals(
                Set.of(
                    DataGenerator.createDefault(
                        IndividualEntityGenerator.class,
                        i -> i.firstName("Victoria").lastName("Theodore")))));

    List<ApplicationSummary> expectedApplicationsSummary =
        expectedApplications.stream().map(this::createApplicationSummary).toList();

    // when
    HarnessResult result =
        getUri(
            TestConstants.URIs.GET_APPLICATIONS
                + "?"
                + SEARCH_STATUS_PARAM
                + ApplicationStatus.APPLICATION_IN_PROGRESS
                + "&"
                + SEARCH_FIRSTNAME_PARAM
                + "George"
                + "&"
                + SEARCH_LASTNAME_PARAM
                + "Theodore"
                + "&"
                + SEARCH_PAGE_PARAM
                + "2");
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 23, 20, 2, 3);
    assertThat(actual.getApplications().size()).isEqualTo(3);
    assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary.subList(20, 23)));
  }

  @Test
  void
      givenApplicationsFilteredByClientDateOfBirth_whenGetAllApplications_thenReturnExpectedApplication()
          throws Exception {
    // given
    LocalDate clientDOB = LocalDate.of(1942, 11, 27);
    List<ApplicationEntity> expectedApplications =
        persistedDataGenerator.createAndPersistMultiple(
            ApplicationEntityGenerator.class,
            2,
            builder ->
                builder
                    .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                    .individuals(
                        Set.of(
                            DataGenerator.createDefault(
                                IndividualEntityGenerator.class,
                                i ->
                                    i.firstName("Jimi")
                                        .lastName("Hendrix")
                                        .dateOfBirth(clientDOB)))));
    var expectedApplicationSummary =
        expectedApplications.stream().map(this::createApplicationSummary).toList();
    persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    // when
    HarnessResult result =
        getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_CLIENTDOB_PARAM + "1942-11-27");
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 2, 20, 1, 2);
    assertThat(actual.getApplications().size()).isEqualTo(2);
    assertArrayEquals(actual.getApplications().toArray(), expectedApplicationSummary.toArray());
  }

  @Test
  void
      givenApplicationFilteredByClientDateOfBirth_whenGetAllApplicationsAndInvalidFormat_thenReturnBadRequest()
          throws Exception {
    HarnessResult result =
        getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_CLIENTDOB_PARAM + "something");
    assertBadRequest(result);
  }

  @Test
  void
      givenApplicationsFilteredByStatusAndNoApplicationsMatch_whenGetApplications_thenReturnEmptyResult()
          throws Exception {
    // given
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        7,
        builder ->
            builder
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .individuals(
                    Set.of(
                        DataGenerator.createDefault(
                            IndividualEntityGenerator.class,
                            i -> i.firstName("George").lastName("Theodore")))));
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        3,
        builder ->
            builder
                .status(ApplicationStatus.APPLICATION_SUBMITTED)
                .individuals(
                    Set.of(
                        DataGenerator.createDefault(
                            IndividualEntityGenerator.class,
                            i -> i.firstName("George").lastName("Theodore")))));
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        2,
        builder ->
            builder
                .status(ApplicationStatus.APPLICATION_SUBMITTED)
                .individuals(
                    Set.of(
                        DataGenerator.createDefault(
                            IndividualEntityGenerator.class,
                            i -> i.firstName("Lucas").lastName("Jones")))));
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        5,
        builder ->
            builder.individuals(
                Set.of(
                    DataGenerator.createDefault(
                        IndividualEntityGenerator.class,
                        i -> i.firstName("Victoria").lastName("Theodore")))));

    // when
    HarnessResult result =
        getUri(
            TestConstants.URIs.GET_APPLICATIONS
                + "?"
                + SEARCH_STATUS_PARAM
                + ApplicationStatus.APPLICATION_IN_PROGRESS
                + "&"
                + SEARCH_FIRSTNAME_PARAM
                + "Lucas"
                + "&"
                + SEARCH_LASTNAME_PARAM
                + "Jones");
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 0, 20, 1, 0);
    assertThat(actual.getApplications().size()).isEqualTo(0);
  }

  @Test
  public void
      givenApplicationsFilteredByCaseworkerJohnDoe_whenGetApplications_thenReturnExpectedApplicationsCorrectly()
          throws Exception {
    // given
    List<ApplicationEntity> expectedApplications =
        persistedDataGenerator.createAndPersistMultiple(
            ApplicationEntityGenerator.class, 4, builder -> builder.caseworker(CaseworkerJohnDoe));

    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class, 6, builder -> builder.caseworker(CaseworkerJaneDoe));

    List<ApplicationSummary> expectedApplicationsSummary =
        expectedApplications.stream().map(this::createApplicationSummary).toList();

    // when
    HarnessResult result =
        getUri(
            TestConstants.URIs.GET_APPLICATIONS
                + "?"
                + SEARCH_CASEWORKERID_PARAM
                + CaseworkerJohnDoe.getId());
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 4, 20, 1, 4);
    assertThat(actual.getApplications().size()).isEqualTo(4);
    assertArrayEquals(expectedApplicationsSummary.toArray(), actual.getApplications().toArray());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void
      givenApplicationFilteredByAutoGrant_whenGetApplications_thenReturnExpectedApplicationsCorrectly(
          boolean isAutoGranted) throws Exception {
    // given
    List<ApplicationEntity> expectedApplications =
        persistedDataGenerator.createAndPersistMultiple(
            ApplicationEntityGenerator.class, 4, builder -> builder.isAutoGranted(isAutoGranted));
    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class, 6, builder -> builder.isAutoGranted(!isAutoGranted));
    List<ApplicationSummary> expectedApplicationsSummary =
        expectedApplications.stream().map(this::createApplicationSummary).toList();

    // when
    HarnessResult result =
        getUri(
            TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_ISAUTOGRANTED_PARAM + isAutoGranted);
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);
    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 4, 20, 1, 4);
    assertThat(actual.getApplications().size()).isEqualTo(4);
    assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
  }

  @Test
  public void
      givenApplicationFilteredByAutoGrant_whenGetApplicationsAndInvalidFormat_thenReturnBadRequest()
          throws Exception {
    HarnessResult result =
        getUri(
            TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_ISAUTOGRANTED_PARAM + "something");
    assertBadRequest(result);
  }

  private static Stream<Arguments> getApplicationSummaryQueryMatterTypes() {
    return Stream.of(Arguments.of(MatterType.SPECIAL_CHILDREN_ACT));
  }

  @ParameterizedTest
  @MethodSource("getApplicationSummaryQueryMatterTypes")
  public void
      givenApplicationFilteredByMatterType_whenGetApplications_thenReturnExpectedApplicationsCorrectly(
          MatterType matterType) throws Exception {
    // given
    List<ApplicationEntity> expectedApplications =
        persistedDataGenerator.createAndPersistMultiple(
            ApplicationEntityGenerator.class, 4, builder -> builder.matterType(matterType));

    List<ApplicationSummary> expectedApplicationsSummary =
        expectedApplications.stream().map(this::createApplicationSummary).toList();

    // when
    HarnessResult result =
        getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_MATTERTYPE_PARAM + matterType);
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);
    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 4, 20, 1, 4);
    assertThat(actual.getApplications().size()).isEqualTo(4);
    assertTrue(actual.getApplications().containsAll(expectedApplicationsSummary));
  }

  @Test
  void givenApplicationWithNoLinkedApplications_whenGetApplications_thenLinkedApplicationsIsEmpty()
      throws Exception {
    // given
    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);
    ApplicationSummary applicationSummary =
        findApplicationSummaryById(actual.getApplications(), application.getId());

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 1, 20, 1, 1);
    assertNotNull(applicationSummary.getLinkedApplications());
    assertTrue(applicationSummary.getLinkedApplications().isEmpty());
  }

  @Test
  void
      givenLeadApplicationOnPage_whenGetApplications_thenLinkedApplicationsContainsAssociatesWithCorrectFields()
          throws Exception {
    // given
    ApplicationEntity leadApplication =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class, builder -> builder.laaReference("LEAD-REF-001"));
    ApplicationEntity associateApplication =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class, builder -> builder.laaReference("ASSOC-REF-001"));
    persistedDataGenerator.persistLink(leadApplication, associateApplication);

    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);
    ApplicationSummary applicationSummary =
        findApplicationSummaryById(actual.getApplications(), leadApplication.getId());
    LinkedApplicationSummaryResponse linkedApplicationSummaryResponse =
        applicationSummary.getLinkedApplications().getFirst();

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 2, 20, 1, 2);
    assertThat(applicationSummary.getLinkedApplications().size()).isEqualTo(1);
    assertThat(linkedApplicationSummaryResponse.getApplicationId())
        .isEqualTo(associateApplication.getId());
    assertThat(linkedApplicationSummaryResponse.getLaaReference())
        .isEqualTo(associateApplication.getLaaReference());
    assertThat(linkedApplicationSummaryResponse.getIsLead()).isFalse();
  }

  @Test
  void
      givenAssociateOnPageAndLeadNotOnPage_whenGetApplications_thenLinkedApplicationsStillPopulated()
          throws Exception {
    // given
    ApplicationEntity associateApplication =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class, builder -> builder.laaReference("ASSOC-REF-001"));

    persistedDataGenerator.createAndPersistMultiple(
        ApplicationEntityGenerator.class,
        19,
        builder -> builder.laaReference("LAA-REF-" + UUID.randomUUID()));

    ApplicationEntity leadApplication =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class, builder -> builder.laaReference("LEAD-REF-001"));

    persistedDataGenerator.persistLink(leadApplication, associateApplication);

    // when
    HarnessResult result =
        getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_PAGE_PARAM + "1");
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);
    ApplicationSummary applicationSummary =
        findApplicationSummaryById(actual.getApplications(), associateApplication.getId());

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 21, 20, 1, 20);
    assertThat(applicationSummary.getLinkedApplications().size()).isEqualTo(1);
    assertThat(applicationSummary.getLinkedApplications().getFirst().getApplicationId())
        .isEqualTo(leadApplication.getId());
  }

  @Test
  void
      givenOnlyAssociateMatchesFilter_whenGetApplications_thenLinkedApplicationsContainsLeadAndSiblingNotInResults()
          throws Exception {
    // given
    ApplicationEntity associateApplication1 =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder ->
                builder
                    .laaReference("ASSOC-REF-001")
                    .individuals(
                        Set.of(
                            DataGenerator.createDefault(
                                IndividualEntityGenerator.class, i -> i.firstName("John")))));
    ApplicationEntity leadApplication =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder ->
                builder
                    .laaReference("LEAD-REF-001")
                    .individuals(
                        Set.of(
                            DataGenerator.createDefault(
                                IndividualEntityGenerator.class, i -> i.firstName("Bob")))));
    ApplicationEntity associateApplication2 =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder ->
                builder
                    .laaReference("ASSOC-REF-002")
                    .individuals(
                        Set.of(
                            DataGenerator.createDefault(
                                IndividualEntityGenerator.class, i -> i.firstName("Charlie")))));

    persistedDataGenerator.persistLink(leadApplication, associateApplication1);
    persistedDataGenerator.persistLink(leadApplication, associateApplication2);

    // when
    HarnessResult result =
        getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_FIRSTNAME_PARAM + "John");
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);
    ApplicationSummary applicationSummary =
        findApplicationSummaryById(actual.getApplications(), associateApplication1.getId());
    assertThat(applicationSummary.getLinkedApplications().size()).isEqualTo(2);

    List<UUID> linkedIds =
        applicationSummary.getLinkedApplications().stream()
            .map(LinkedApplicationSummaryResponse::getApplicationId)
            .toList();

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, 1, 20, 1, 1);
    assertThat(actual.getApplications().size()).isEqualTo(1);
    assertTrue(linkedIds.contains(leadApplication.getId()));
    assertTrue(linkedIds.contains(associateApplication2.getId()));
  }

  @SmokeTest
  @Test
  public void givenNoUser_whenGetApplications_thenReturnUnauthorised() throws Exception {
    withNoToken();
    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);

    assertSecurityHeaders(result);
    assertUnauthorised(result);
  }

  @Test
  public void givenNoRole_whenGetApplications_thenReturnForbidden() throws Exception {
    withToken(TestConstants.Tokens.UNKNOWN);
    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATIONS);

    assertSecurityHeaders(result);
    assertForbidden(result);
  }

  private void assertPaging(
      ApplicationSummaryResponse applicationSummaryResponse,
      Integer totalRecords,
      Integer pageSize,
      Integer page,
      Integer itemsReturned) {
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
        Arguments.of("ANE", "Jane", 6));
  }

  private static Stream<Arguments> lastNameSearchCases() {
    return Stream.of(
        Arguments.of("Smith", "Smith", 3),
        Arguments.of("Smi", "Smith", 7),
        Arguments.of("ith", "Smith", 5),
        Arguments.of("mit", "Smith", 8),
        Arguments.of("MITH", "Smith", 4));
  }

  @ParameterizedTest
  @MethodSource("applicationsSummaryFilteredByStatusCases")
  void
      givenApplicationsFilteredByStatus_whenGetApplications_thenReturnExpectedApplicationsCorrectly(
          ApplicationStatus status, int numberOfApplications) throws Exception {
    // given
    List<ApplicationSummary> expectedApplicationsSummary =
        generateApplicationSummaries(status, numberOfApplications);

    // when
    HarnessResult result =
        getUri(TestConstants.URIs.GET_APPLICATIONS + "?" + SEARCH_STATUS_PARAM + status);
    ApplicationSummaryResponse actual = deserialise(result, ApplicationSummaryResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertPaging(actual, numberOfApplications, 20, 1, numberOfApplications);
    assertThat(actual.getApplications().size()).isEqualTo(numberOfApplications);
    assertTrue((actual.getApplications()).containsAll(expectedApplicationsSummary));
  }

  private static Stream<Arguments> applicationsSummaryFilteredByStatusCases() {
    return Stream.of(
        Arguments.of(ApplicationStatus.APPLICATION_IN_PROGRESS, 8),
        Arguments.of(ApplicationStatus.APPLICATION_SUBMITTED, 5));
  }

  private List<ApplicationSummary> generateApplicationSummaries(
      ApplicationStatus status, int numberOfApplications) {
    Random random = new Random();
    return persistedDataGenerator
        .createAndPersistMultiple(
            ApplicationEntityGenerator.class,
            numberOfApplications,
            builder -> builder.status(status).laaReference("REF-00" + random.nextInt(100)))
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
    applicationSummary.setUsedDelegatedFunctions(applicationEntity.getUsedDelegatedFunctions());
    applicationSummary.setCategoryOfLaw(applicationEntity.getCategoryOfLaw());
    applicationSummary.setMatterType(applicationEntity.getMatterType());
    applicationSummary.setAssignedTo(
        applicationEntity.getCaseworker() != null
            ? applicationEntity.getCaseworker().getId()
            : null);
    applicationSummary.autoGrant(applicationEntity.getIsAutoGranted());
    applicationSummary.setLaaReference(applicationEntity.getLaaReference());
    applicationSummary.setApplicationType(ApplicationType.INITIAL);
    applicationSummary.setClientFirstName(
        applicationEntity.getIndividuals().stream().findFirst().get().getFirstName());
    applicationSummary.setClientLastName(
        applicationEntity.getIndividuals().stream().findFirst().get().getLastName());
    applicationSummary.setClientDateOfBirth(
        applicationEntity.getIndividuals().stream().findFirst().get().getDateOfBirth());
    applicationSummary.setIsLead(applicationEntity.isLead());
    applicationSummary.setOfficeCode(applicationEntity.getOfficeCode());
    return applicationSummary;
  }

  private ApplicationSummary findApplicationSummaryById(
      List<ApplicationSummary> applicationSummaries, UUID id) {
    return applicationSummaries.stream()
        .filter(application -> application.getApplicationId().equals(id))
        .findFirst()
        .orElseThrow();
  }
}

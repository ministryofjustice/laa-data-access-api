package uk.gov.justice.laa.dstew.access.controller.individual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.of;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertBadRequest;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertContentHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ProblemDetail;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.IndividualResponse;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.harness.BaseHarnessTest;
import uk.gov.justice.laa.dstew.access.utils.harness.HarnessResult;
import uk.gov.justice.laa.dstew.access.utils.harness.SmokeTest;

public class GetIndividualsTest extends BaseHarnessTest {

  @SmokeTest
  @ParameterizedTest
  @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
  void givenPagingParametersAndInvalidHeader_whenGetIndividuals_thenReturnBadRequest(
          String serviceName
  ) throws Exception {
    verifyServiceNameHeader(serviceName);
  }

  @SmokeTest
  @Test
  void givenPagingParametersAndNoHeader_whenGetIndividuals_thenReturnBadRequest() throws Exception {
    verifyServiceNameHeader(null);
  }

  private void verifyServiceNameHeader(String serviceName) throws Exception {
    int page = 1;
    int pageSize = 20;

    HarnessResult result = getUri(
        TestConstants.URIs.GET_INDIVIDUALS + "?page=" + page + "&pageSize=" + pageSize,
        ServiceNameHeader(serviceName));
    applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
  }

  @Test
  void givenIncludeParametersAndNoAppId_whenGetIndividuals_thenReturnBadRequest() throws Exception {
    HarnessResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS + "?include=CLIENT_DETAILS");
    assertBadRequest(result);
    ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
    assertThat(problemDetail.getProperties())
            .containsEntry("errors", List.of("Application ID is required when included data is CLIENT_DETAILS"));
  }

  @Test
  void givenIncludeParametersAndAppId_whenGetIndividuals_thenProcessCorrectly() throws Exception {
    ApplicationEntity application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
    persistedDataGenerator.createAndPersist(IndividualEntityGenerator.class,
            builder -> builder.applications(Set.of(application)));
    HarnessResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS + "?include=CLIENT_DETAILS&individualType=CLIENT&applicationId="+application.getId().toString());

    assertOK(result);
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);
    IndividualResponse actualIndividual = response.getIndividuals().getFirst();
    assertThat(actualIndividual.getRelationshipToInvolvedChildren()).isEqualTo("relationshipToChildren");
    assertThat(actualIndividual.getLastNameAtBirth()).isEqualTo("Alberts");
    assertThat(actualIndividual.getPreviousApplicationId()).isEqualTo("ZZ999Z");
    assertThat(actualIndividual.getCorrespondenceAddressType()).isEqualTo("Home");
    assertThat(actualIndividual.getAppliedPreviously()).isTrue();
    assertThat(actualIndividual.getCorrespondenceAddress()).hasSize(2);
  }

  @ParameterizedTest
  @MethodSource("pagingParameters")
  void givenPagingParameters_whenGetIndividuals_thenCorrectPagingInResponse(
      Integer page,
      Integer pageSize,
      int expectedPage,
      int expectedPageSize,
      int totalEntities,
      int expectedReturned,
      int expectedTotalRecords
  ) throws Exception {
    // given
    persistedDataGenerator.createAndPersistMultiple(IndividualEntityGenerator.class, totalEntities);
    // when
    HarnessResult result = getUri(
        TestConstants.URIs.GET_INDIVIDUALS + "?page=" + page + "&pageSize=" + pageSize);
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);
    // then
    assertOK(result);
    assertThat(response.getPaging().getPage()).isEqualTo(expectedPage);
    assertThat(response.getPaging().getPageSize()).isEqualTo(expectedPageSize);
    assertThat(response.getPaging().getItemsReturned()).isEqualTo(expectedReturned);
    assertThat(response.getPaging().getTotalRecords()).isEqualTo(expectedTotalRecords);
  }

  static Stream<org.junit.jupiter.params.provider.Arguments> pagingParameters() {
    return Stream.of(
        // page, pageSize, expectedPage, expectedPageSize, totalEntities, expectedReturned, expectedTotalRecords
        of(1, 10, 1, 10, 15, 10, 15), // first page, 10 of 15
        of(2, 10, 2, 10, 15, 5, 15), // second page, 5 of 15
        of(100, 10, 100, 10, 15, 0, 15) // page beyond data, empty
    );
  }

  @ParameterizedTest
  @MethodSource("invalidPagingParameters")
  void givenInvalidPagingParameters_whenGetIndividuals_thenReturnBadRequest(
      Integer page,
      Integer pageSize
  ) throws Exception {
    HarnessResult result = getUri(
        TestConstants.URIs.GET_INDIVIDUALS + "?page=" + page + "&pageSize=" + pageSize);
    assertBadRequest(result);
  }

  static Stream<org.junit.jupiter.params.provider.Arguments> invalidPagingParameters() {
    return Stream.of(
        of(0, 10),
        of(-1, 10),
        of(1, 0),
        of(1, -74),
        of(1, 101),
        of(0, 0)
    );
  }

  @Test
  public void givenExistingIndividual_whenGetIndividuals_thenReturnOKWithCorrectData() throws Exception {
    // given
    IndividualEntity persisted = persistedDataGenerator.createAndPersist(IndividualEntityGenerator.class);
    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS);
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);
    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    assertThat(response.getIndividuals()).isNotEmpty();
    assertThat(response.getPaging().getTotalRecords()).isEqualTo(1);
    assertThat(response.getPaging().getItemsReturned()).isEqualTo(1);
    assertThat(response.getPaging().getPage()).isEqualTo(1);
    assertThat(response.getPaging().getPageSize()).isEqualTo(20);
    assertThat(response.getIndividuals()).hasSize(1);
    assertThat(response.getIndividuals().getFirst().getFirstName()).isEqualTo(persisted.getFirstName());
    assertThat(response.getIndividuals().getFirst().getLastName()).isEqualTo(persisted.getLastName());
  }

  @Test
  public void givenUnknownRole_whenGetIndividuals_thenReturnForbiddenResponse() throws Exception {
    withToken(TestConstants.Tokens.UNKNOWN);
    HarnessResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS);
    assertSecurityHeaders(result);
    assertForbidden(result);
  }

  @SmokeTest
  @Test
  public void givenNoUser_whenGetIndividuals_thenReturnUnauthorisedResponse() throws Exception {
    withNoToken();
    HarnessResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS);
    assertSecurityHeaders(result);
    assertUnauthorised(result);
  }

  @Test
  void givenNullPageAndPageSize_whenGetIndividuals_thenDefaultsAreApplied() throws Exception {
    // given
    persistedDataGenerator.createAndPersistMultiple(IndividualEntityGenerator.class, 5);
    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS);
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);
    // then
    assertOK(result);
    assertThat(response.getPaging().getPage()).isEqualTo(1);
    assertThat(response.getPaging().getPageSize()).isEqualTo(20);
    assertThat(response.getPaging().getItemsReturned()).isEqualTo(5);
    assertThat(response.getPaging().getTotalRecords()).isEqualTo(5);
  }

  @Test
  void givenApplicationId_whenGetIndividuals_thenFiltersByApplicationId() throws Exception {
    // given
    IndividualEntity individual = DataGenerator.createDefault(IndividualEntityGenerator.class);
    var application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class,
        builder -> builder.individuals(Set.of(individual)));
    persistedDataGenerator.createAndPersist(IndividualEntityGenerator.class); // unrelated individual
    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS + "?applicationId=" + application.getId());
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);
    // then
    assertOK(result);
    assertThat(response.getIndividuals()).hasSize(1);
    assertThat(response.getIndividuals().getFirst().getFirstName()).isEqualTo(individual.getFirstName());
    assertThat(response.getIndividuals().getFirst().getLastName()).isEqualTo(individual.getLastName());
  }

  @Test
  void givenIndividualType_whenGetIndividuals_thenFiltersByIndividualType() throws Exception {
    // given
    persistedDataGenerator.createAndPersist(IndividualEntityGenerator.class,
        builder -> builder.type(IndividualType.CLIENT));
    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS + "?individualType=CLIENT");
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);
    // then
    assertOK(result);
    assertThat(response.getIndividuals()).hasSize(1);
    assertThat(response.getIndividuals().getFirst().getType()).isEqualTo(IndividualType.CLIENT);
  }

  @Test
  void givenBothFilters_whenGetIndividuals_thenFiltersByApplicationIdAndIndividualType() throws Exception {
    // given
    IndividualEntity client = DataGenerator.createDefault(IndividualEntityGenerator.class,
        builder -> builder.type(IndividualType.CLIENT));
    var application = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class,
        builder -> builder.individuals(Set.of(client)));
    persistedDataGenerator.createAndPersist(IndividualEntityGenerator.class,
        builder -> builder.type(IndividualType.CLIENT));
    // when
    HarnessResult result = getUri(
        TestConstants.URIs.GET_INDIVIDUALS + "?applicationId=" + application.getId() + "&individualType=CLIENT");
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);
    // then
    assertOK(result);
    assertThat(response.getIndividuals()).hasSize(1);
    assertThat(response.getIndividuals().getFirst().getFirstName()).isEqualTo(client.getFirstName());
    assertThat(response.getIndividuals().getFirst().getLastName()).isEqualTo(client.getLastName());
    assertThat(response.getIndividuals().getFirst().getType()).isEqualTo(IndividualType.CLIENT);
  }

  @Test
  void givenInvalidApplicationId_whenGetIndividuals_thenReturnsBadRequest() throws Exception {
    HarnessResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS + "?applicationId=not-a-uuid");
    assertBadRequest(result);
  }

  @Test
  void givenInvalidIndividualType_whenGetIndividuals_thenReturnsBadRequest() throws Exception {
    HarnessResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS + "?individualType=NOT_A_TYPE");
    assertBadRequest(result);
  }

  @Test
  void givenLowercaseIndividualType_whenGetIndividuals_thenReturnsBadRequest() throws Exception {
    HarnessResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS + "?individualType=client");
    assertBadRequest(result);
  }

  @Test
  void givenNoMatchingFilters_whenGetIndividuals_thenReturnsEmptyList() throws Exception {
    // given
    IndividualEntity client = DataGenerator.createDefault(IndividualEntityGenerator.class,
        builder -> builder.type(IndividualType.CLIENT));
    persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class,
        builder -> builder.individuals(Set.of(client)));
    // when
    HarnessResult result = getUri(
        TestConstants.URIs.GET_INDIVIDUALS + "?applicationId=" + UUID.randomUUID() + "&individualType=CLIENT");
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);
    // then
    assertOK(result);
    assertThat(response.getIndividuals()).isEmpty();
    assertThat(response.getPaging().getItemsReturned()).isEqualTo(0);
  }

  @Test
  void givenNonExistentApplicationId_whenGetIndividuals_thenReturnsEmptyList() throws Exception {
    // given
    persistedDataGenerator.createAndPersist(IndividualEntityGenerator.class,
        builder -> builder.type(IndividualType.CLIENT));
    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS + "?applicationId=" + UUID.randomUUID());
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);
    // then
    assertOK(result);
    assertThat(response.getIndividuals()).isEmpty();
  }

  @Test
  void givenMultipleIndividualsLinkedToApplication_whenGetIndividuals_thenReturnsAllLinked() throws Exception {
    // given
    IndividualEntity individual1 = DataGenerator.createDefault(IndividualEntityGenerator.class);
    IndividualEntity individual2 = DataGenerator.createDefault(IndividualEntityGenerator.class);
    IndividualEntity individual3 = DataGenerator.createDefault(IndividualEntityGenerator.class);
    var application = persistedDataGenerator.createAndPersist(
        ApplicationEntityGenerator.class,
        builder -> builder.individuals(Set.of(individual1, individual2, individual3))
    );
    persistedDataGenerator.createAndPersist(IndividualEntityGenerator.class); // unrelated individual
    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS + "?applicationId=" + application.getId());
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);
    // then
    assertOK(result);
    assertThat(response.getIndividuals()).hasSize(3);
    assertThat(response.getIndividuals())
        .extracting(IndividualResponse::getFirstName)
        .containsExactlyInAnyOrder(individual1.getFirstName(), individual2.getFirstName(), individual3.getFirstName());
  }

  @Test
  void givenIndividualLinkedToMultipleApplications_whenGetIndividuals_thenReturnsIndividual() throws Exception {
    // given — persist the individual first so it has an ID, then link to both applications
    IndividualEntity sharedIndividual = persistedDataGenerator.createAndPersist(IndividualEntityGenerator.class);
    var application1 = persistedDataGenerator.createAndPersistWithPersistedIndividuals(
        builder -> builder.individuals(Set.of(sharedIndividual))
    );
    persistedDataGenerator.createAndPersistWithPersistedIndividuals(
        builder -> builder.individuals(Set.of(sharedIndividual))
    );
    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS + "?applicationId=" + application1.getId());
    IndividualsResponse response = deserialise(result, IndividualsResponse.class);
    // then
    assertOK(result);
    assertThat(response.getIndividuals()).hasSize(1);
    assertThat(response.getIndividuals().getFirst().getFirstName()).isEqualTo(sharedIndividual.getFirstName());
  }

  @Test
  public void givenNoUser_whenGetIndividualsWithFilters_thenReturnUnauthorisedResponse() throws Exception {
    withNoToken();
    HarnessResult result = getUri(
        TestConstants.URIs.GET_INDIVIDUALS + "?applicationId=" + UUID.randomUUID() + "&individualType=CLIENT");
    assertSecurityHeaders(result);
    assertUnauthorised(result);
  }

  @Test
  public void givenUnknownRole_whenGetIndividualsWithFilters_thenReturnForbiddenResponse() throws Exception {
    withToken(TestConstants.Tokens.UNKNOWN);
    HarnessResult result = getUri(
        TestConstants.URIs.GET_INDIVIDUALS + "?applicationId=" + UUID.randomUUID() + "&individualType=CLIENT");
    assertSecurityHeaders(result);
    assertForbidden(result);
  }

  @Test
  public void givenWriterRole_whenGetIndividuals_thenReturnForbiddenResponse() throws Exception {
    withToken(TestConstants.Tokens.UNKNOWN);
    HarnessResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS);
    assertSecurityHeaders(result);
    assertForbidden(result);
  }

  @Test
  public void givenWriterRole_whenGetIndividualsWithFilters_thenReturnForbiddenResponse() throws Exception {
    withToken(TestConstants.Tokens.UNKNOWN);
    HarnessResult result = getUri(
        TestConstants.URIs.GET_INDIVIDUALS + "?applicationId=" + UUID.randomUUID() + "&individualType=CLIENT");
    assertSecurityHeaders(result);
    assertForbidden(result);
  }

  @Test
  public void givenUserWithNoAuthorities_whenGetIndividuals_thenReturnForbiddenResponse() throws Exception {
    withToken(TestConstants.Tokens.UNKNOWN);
    HarnessResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS);
    assertSecurityHeaders(result);
    assertForbidden(result);
  }

  @Test
  public void givenUserWithNoAuthorities_whenGetIndividualsWithPaging_thenReturnForbiddenResponse() throws Exception {
    withToken(TestConstants.Tokens.UNKNOWN);
    HarnessResult result = getUri(TestConstants.URIs.GET_INDIVIDUALS + "?page=1&pageSize=10");
    assertSecurityHeaders(result);
    assertForbidden(result);
  }
}

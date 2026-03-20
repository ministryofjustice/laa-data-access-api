package uk.gov.justice.laa.dstew.access.controller.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertContentHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertForbidden;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNoCacheHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNotFound;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertUnauthorised;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ProblemDetail;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceedingResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.OpponentResponse;
import uk.gov.justice.laa.dstew.access.model.ProviderResponse;
import uk.gov.justice.laa.dstew.access.model.ScopeLimitationResponse;
import uk.gov.justice.laa.dstew.access.utils.EnumParsingUtils;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationContentGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationMeritsGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.harness.BaseHarnessTest;
import uk.gov.justice.laa.dstew.access.utils.harness.HarnessResult;
import uk.gov.justice.laa.dstew.access.utils.harness.SmokeTest;

public class GetApplicationTest extends BaseHarnessTest {

  @SmokeTest
  @ParameterizedTest
  @ValueSource(strings = {"", "invalid-header", "CIVIL-APPLY", "civil_apply"})
  void givenApplicationDataAndIncorrectHeader_whenGetApplications_thenReturnBadRequest(
      String serviceName) throws Exception {
    verifyBadServiceNameHeader(serviceName);
  }

  @SmokeTest
  @Test
  void givenApplicationDataAndNoHeader_whenGetApplication_thenReturnBadRequest() throws Exception {
    verifyBadServiceNameHeader(null);
  }

  private void verifyBadServiceNameHeader(String serviceName) throws Exception {
    HarnessResult result =
        getUri(
            TestConstants.URIs.GET_APPLICATION, ServiceNameHeader(serviceName), UUID.randomUUID());

    applicationAsserts.assertErrorGeneratedByBadHeader(result, serviceName);
  }

  @SmokeTest
  @Test
  public void givenExistingApplication_whenGetApplication_thenReturnOKWithCorrectData()
      throws Exception {
    // given
    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> builder.caseworker(CaseworkerJohnDoe).linkedApplications(Set.of()));

    ProceedingEntity proceeding =
        persistedDataGenerator.createAndPersist(
            ProceedingsEntityGenerator.class,
            builder -> {
              builder.applicationId(application.getId());
            });

    DecisionEntity decision =
        persistedDataGenerator.createAndPersist(
            DecisionEntityGenerator.class,
            builder -> {
              builder.meritsDecisions(
                  Set.of(
                      DataGenerator.createDefault(
                          MeritsDecisionsEntityGenerator.class,
                          mBuilder -> {
                            mBuilder.proceeding(proceeding);
                          })));
            });

    application.setDecision(decision);
    ApplicationEntity savedApplication = persistedDataGenerator.updateAndFlush(application);
    savedApplication.setVersion(1L); // now changed..

    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
    ApplicationResponse actualApplication = deserialise(result, ApplicationResponse.class);

    // then
    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);
    ApplicationResponse expectedApplication =
        createApplication(savedApplication, proceeding, decision);
    Assertions.assertThat(actualApplication)
        .usingRecursiveComparison()
        .ignoringFields("lastUpdated")
        .isEqualTo(expectedApplication);
    Assertions.assertThat(actualApplication.getLastUpdated()).isNotNull();
  }

  @Test
  public void givenApplicationNotExist_whenGetApplication_thenReturnNotFound() throws Exception {
    // given
    UUID notExistApplicationId = UUID.randomUUID();

    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, notExistApplicationId);

    // then
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertNotFound(result);
    assertEquals("application/problem+json", result.getResponse().getHeader("Content-Type"));
    ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
    assertEquals(
        "No application found with id: " + notExistApplicationId, problemDetail.getDetail());
  }

  @Test
  public void givenUnknownRole_whenGetApplication_thenReturnForbidden() throws Exception {
    // given
    withToken(TestConstants.Tokens.UNKNOWN);
    ApplicationEntity expectedApplication =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, expectedApplication.getId());

    // then
    assertSecurityHeaders(result);
    assertForbidden(result);
  }

  @Test
  public void givenNoUser_whenGetApplication_thenReturnUnauthorised() throws Exception {
    // given
    withNoToken();
    ApplicationEntity expectedApplication =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    // when
    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, expectedApplication.getId());

    // then
    assertSecurityHeaders(result);
    assertUnauthorised(result);
  }

  @Test
  void givenApplicationWithOpponents_whenGetApplication_thenReturnsOpponents() throws Exception {

    // default contains opponent data
    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder ->
                builder
                    .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                    .createdAt(Instant.now().minusSeconds(10000))
                    .modifiedAt(Instant.now()));

    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
    ApplicationResponse response = deserialise(result, ApplicationResponse.class);

    assertContentHeaders(result);
    assertSecurityHeaders(result);
    assertNoCacheHeaders(result);
    assertOK(result);

    Assertions.assertThat(response.getOpponents()).isNotNull();
    Assertions.assertThat(response.getOpponents()).hasSize(1);

    var mapped = response.getOpponents().get(0);
    Assertions.assertThat(mapped.getOpposableType()).isEqualTo("ApplicationMeritsTask::Individual");
    Assertions.assertThat(mapped.getFirstName()).isEqualTo("John");
    Assertions.assertThat(mapped.getLastName()).isEqualTo("Smith");
    Assertions.assertThat(mapped.getOrganisationName()).isEqualTo("Acme Ltd");
  }

  @Test
  void givenApplicationWithEmptyOpponents_whenGetApplication_thenReturnsEmptyList()
      throws Exception {

    ApplicationContent applicationContent =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class,
            builder ->
                builder.applicationMerits(
                    DataGenerator.createDefault(
                        ApplicationMeritsGenerator.class,
                        meritsBuilder -> meritsBuilder.opponents(List.of()))));

    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder ->
                builder.applicationContent(
                    objectMapper.convertValue(applicationContent, Map.class)));

    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
    ApplicationResponse response = deserialise(result, ApplicationResponse.class);

    assertOK(result);
    Assertions.assertThat(response.getOpponents()).isNotNull();
    Assertions.assertThat(response.getOpponents()).isEmpty();
  }

  @Test
  void givenApplicationWithoutOpponentsSection_whenGetApplication_thenOpponentsIsEmpty()
      throws Exception {

    Map<String, Object> content = Map.of("someOtherKey", "value");

    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class, builder -> builder.applicationContent(content));

    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
    ApplicationResponse response = deserialise(result, ApplicationResponse.class);

    assertOK(result);
    Assertions.assertThat(response.getOpponents()).isEmpty();
  }

  @Test
  void givenOpponentWithMissingFirstName_whenGetApplication_thenReturnsRemainingFields()
      throws Exception {

    // leaving this here as a TODO as it does something specific that the test harness currently
    // does not,
    // i.e. test that a key in the JSON completely missing does not break the functionality.

    Map<String, Object> opposable =
        Map.of(
            "opposableType", "ApplicationMeritsTask::Individual",
            // firstName intentionally missing
            "lastName", "Smith",
            "name", "Acme Ltd");

    Map<String, Object> opponent = Map.of("opposable", opposable);

    Map<String, Object> merits = Map.of("opponents", List.of(opponent));

    Map<String, Object> content = Map.of("applicationMerits", merits);

    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class, builder -> builder.applicationContent(content));

    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
    ApplicationResponse response = deserialise(result, ApplicationResponse.class);

    assertOK(result);

    Assertions.assertThat(response.getOpponents()).isNotNull();
    Assertions.assertThat(response.getOpponents()).hasSize(1);

    var mapped = response.getOpponents().get(0);
    Assertions.assertThat(mapped.getOpposableType()).isEqualTo("ApplicationMeritsTask::Individual");
    Assertions.assertThat(mapped.getFirstName()).isNull();
    Assertions.assertThat(mapped.getLastName()).isEqualTo("Smith");
    Assertions.assertThat(mapped.getOrganisationName()).isEqualTo("Acme Ltd");
  }

  @Test
  void givenApplicationWithSubmitterEmail_whenGetApplication_thenReturnsProviderWithContactEmail()
      throws Exception {

    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
    ApplicationResponse response = deserialise(result, ApplicationResponse.class);

    assertOK(result);
    Assertions.assertThat(response.getProvider()).isNotNull();
    Assertions.assertThat(response.getProvider().getOfficeCode()).isEqualTo("officeCode");
    Assertions.assertThat(response.getProvider().getContactEmail()).isEqualTo("test@example.com");
  }

  @Test
  void
      givenApplicationWithoutSubmitterEmail_whenGetApplication_thenReturnsProviderWithoutContactEmail()
          throws Exception {

    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder -> builder.applicationContent(Map.of("someOtherKey", "value")));

    HarnessResult result = getUri(TestConstants.URIs.GET_APPLICATION, application.getId());
    ApplicationResponse response = deserialise(result, ApplicationResponse.class);

    assertOK(result);
    Assertions.assertThat(response.getProvider()).isNotNull();
    Assertions.assertThat(response.getProvider().getOfficeCode()).isEqualTo("officeCode");
    Assertions.assertThat(response.getProvider().getContactEmail()).isNull();
  }

  private ApplicationResponse createApplication(
      ApplicationEntity applicationEntity, ProceedingEntity proceeding, DecisionEntity decision) {
    ApplicationResponse application = new ApplicationResponse();
    application.setApplicationId(applicationEntity.getId());
    application.setStatus(applicationEntity.getStatus());
    application.setLaaReference(applicationEntity.getLaaReference());
    if (applicationEntity.getCaseworker() != null) {
      application.setAssignedTo(applicationEntity.getCaseworker().getId());
    }
    application.setLastUpdated(
        OffsetDateTime.ofInstant(applicationEntity.getUpdatedAt(), ZoneOffset.UTC));
    application.setLastUpdated(
        OffsetDateTime.ofInstant(applicationEntity.getUpdatedAt(), ZoneOffset.UTC));
    application.setSubmittedAt(
        applicationEntity.getSubmittedAt() != null
            ? OffsetDateTime.ofInstant(applicationEntity.getSubmittedAt(), ZoneOffset.UTC)
            : null);
    application.setUsedDelegatedFunctions(applicationEntity.getUsedDelegatedFunctions());
    application.setAutoGrant(applicationEntity.getIsAutoGranted());
    if (applicationEntity.getDecision() != null) {
      application.setOverallDecision(applicationEntity.getDecision().getOverallDecision());
    }
    application.isLead(applicationEntity.isLead());

    // Extract provider with both officeCode and contactEmail
    String officeCode = applicationEntity.getOfficeCode();
    String contactEmail = extractContactEmail(applicationEntity.getApplicationContent());

    if (officeCode != null || contactEmail != null) {
      ProviderResponse providerResponse = new ProviderResponse();
      providerResponse.setOfficeCode(officeCode);
      providerResponse.setContactEmail(contactEmail);
      application.setProvider(providerResponse);
    }

    Map<String, Object> applicationMerits =
        applicationEntity.getApplicationContent() != null
            ? (Map<String, Object>)
                applicationEntity.getApplicationContent().get("applicationMerits")
            : null;

    List<ScopeLimitationResponse> scopeLimitations = null;
    if (proceeding.getProceedingContent().get("scopeLimitations") != null) {
      scopeLimitations =
          ((List<Map<String, Object>>) proceeding.getProceedingContent().get("scopeLimitations"))
              .stream()
                  .map(
                      sl ->
                          ScopeLimitationResponse.builder()
                              .scopeLimitation(
                                  sl.get("meaning") != null ? sl.get("meaning").toString() : null)
                              .scopeDescription(
                                  sl.get("description") != null
                                      ? sl.get("description").toString()
                                      : null)
                              .build())
                  .toList();
    }

    application.setProceedings(
        List.of(
            ApplicationProceedingResponse.builder()
                .proceedingId(proceeding.getId())
                .proceedingDescription(proceeding.getDescription())
                .proceedingType(proceeding.getProceedingContent().get("meaning").toString())
                .categoryOfLaw(
                    EnumParsingUtils.convertToCategoryOfLaw(
                        (String) proceeding.getProceedingContent().get("categoryOfLaw")))
                .matterType(
                    EnumParsingUtils.convertToMatterType(
                        proceeding.getProceedingContent().get("matterType").toString()))
                .levelOfService(
                    proceeding
                        .getProceedingContent()
                        .get("substantiveLevelOfServiceName")
                        .toString())
                .substantiveCostLimitation(
                    proceeding.getProceedingContent().get("substantiveCostLimitation").toString())
                .delegatedFunctionsDate(
                    LocalDate.parse(
                        proceeding
                            .getProceedingContent()
                            .get("usedDelegatedFunctionsOn")
                            .toString()))
                .meritsDecision(decision.getMeritsDecisions().iterator().next().getDecision())
                // .involvedChildren((List<Object>) applicationMerits.get("involvedChildren"))
                .scopeLimitations(scopeLimitations)
                .build()));

    application.setOpponents(
        applicationEntity.getApplicationContent() != null
            ? applicationEntity.getApplicationContent().get("applicationMerits") != null
                ? extractOpponents(applicationEntity.getApplicationContent())
                : List.of()
            : List.of());

    application.setVersion(applicationEntity.getVersion());
    return application;
  }

  private List<OpponentResponse> extractOpponents(Map<String, Object> applicationContent) {
    Map<String, Object> merits = (Map<String, Object>) applicationContent.get("applicationMerits");
    List<Map<String, Object>> opponents = (List<Map<String, Object>>) merits.get("opponents");

    return opponents.stream()
        .map(
            opponent -> {
              Map<String, Object> opposable = (Map<String, Object>) opponent.get("opposable");
              return OpponentResponse.builder()
                  .opposableType(opposable.get("opposableType").toString())
                  .firstName(
                      opposable.get("firstName") != null
                          ? opposable.get("firstName").toString()
                          : null)
                  .lastName(
                      opposable.get("lastName") != null
                          ? opposable.get("lastName").toString()
                          : null)
                  .organisationName(
                      opposable.get("name") != null ? opposable.get("name").toString() : null)
                  .build();
            })
        .toList();
  }

  private static String extractContactEmail(Map<String, Object> content) {
    if (content == null) {
      return null;
    }
    Object submitterEmail = content.get("submitterEmail");
    return submitterEmail instanceof String ? (String) submitterEmail : null;
  }
}

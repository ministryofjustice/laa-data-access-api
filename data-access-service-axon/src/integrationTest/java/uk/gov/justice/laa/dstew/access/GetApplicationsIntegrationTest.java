package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.applicationWithMatterPairs;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validAddressContent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validApplicationContent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkType;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.AutoGrantedOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.DecisionStatusResponse;
import uk.gov.justice.laa.dstew.access.model.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatusResponse;
import uk.gov.justice.laa.dstew.access.model.PriorAuthoritySummary;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftResponse;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.FindPriorAuthorityByPriorAuthorityIdQuery;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

/** Full HTTP/Postgres/Axon integration tests for the GET /applications (list) endpoint. */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"feature.enable-dev-token=true"})
@AutoConfigureTestRestTemplate
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GetApplicationsIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private QueryGateway queryGateway;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void givenCreatedApplication_whenGetApplications_thenReturnsApplicationInList() {
    UUID applicationId = UUID.randomUUID();
    createApplication(applicationId);
    awaitApplicationProjection(applicationId);

    ResponseEntity<ApplicationSummaryResponse> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications?pageSize=100",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationSummaryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getPaging()).isNotNull();
    assertThat(response.getBody().getApplications())
        .extracting(application -> application.getApplicationId())
        .contains(applicationId);
    ApplicationSummary application = findApplication(response, applicationId);
    assertThat(application.getPriorAuthorities()).isEmpty();
  }

  @Test
  void givenSubmittedApplication_whenGetById_thenReturnsCreateContent() {
    UUID applicationId = UUID.randomUUID();
    UUID firstProceedingId = UUID.randomUUID();
    UUID secondProceedingId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applicationId, firstProceedingId);
    Map<String, Object> content = new HashMap<>(request.getApplicationContent());
    Map<String, Object> client = new HashMap<>((Map<String, Object>) content.get("client"));
    client.put("lastNameAtBirth", "Byron");
    client.put("hasNationalInsuranceNumber", true);
    client.put("nationalInsuranceNumber", "QQ123456C");
    client.put("previousApplicationId", "PREVIOUS-123");
    client.put("relationshipToInvolvedChildren", "Parent");
    Map<String, Object> addressContent = new HashMap<>(validAddressContent());
    addressContent.put("addressLineTwo", "Suite 2");
    addressContent.put("addressLineThree", "Historic district");
    addressContent.put("county", "Greater London");
    addressContent.put("organisation", "Analytical Engines Ltd");
    addressContent.put("buildingNumberName", "Engine House");
    addressContent.put("lookupUsed", true);
    addressContent.put("careOf", "PERSON");
    addressContent.put("careOfFirstName", "Augusta");
    addressContent.put("careOfLastName", "King");
    addressContent.put("careOfOrganisationName", "Engine House Ltd");
    client.put("addresses", List.of(addressContent));
    content.put("client", client);
    Map<String, Object> firstProceeding =
        new HashMap<>((Map<String, Object>) ((List<?>) content.get("proceedings")).getFirst());
    firstProceeding.put("meaning", "Meaning one");
    firstProceeding.put("description", "Description one");
    firstProceeding.put("categoryOfLaw", "New category");
    firstProceeding.put("categoryOfLawCode", "CAT-001");
    firstProceeding.put("matterType", "New matter");
    firstProceeding.put("matterTypeCode", "MAT-001");
    firstProceeding.put("substantiveCostLimitation", "2500.25");
    Map<String, Object> secondProceeding = new HashMap<>(firstProceeding);
    secondProceeding.put("id", secondProceedingId.toString());
    secondProceeding.put("leadProceeding", false);
    secondProceeding.put("meaning", "Meaning two");
    secondProceeding.put("description", "Description two");
    secondProceeding.put("matterType", "Second matter");
    secondProceeding.put("matterTypeCode", "MAT-002");
    content.put("proceedings", List.of(firstProceeding, secondProceeding));
    content.put(
        "opponents",
        List.of(Map.of("opponentType", "INDIVIDUAL", "firstName", "Rae", "lastName", "Opponent")));
    request.setApplicationContent(content);
    request.setLaaReference("DETAIL-" + applicationId);
    assertThat(
            restTemplate
                .postForEntity(
                    "http://localhost:" + port + "/api/v0/applications",
                    new HttpEntity<>(request, headers()),
                    Void.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    awaitApplicationProjection(applicationId);

    ResponseEntity<ApplicationResponse> response = getApplication(applicationId);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ApplicationResponse detail = response.getBody();
    assertThat(detail).isNotNull();
    assertThat(detail.getApplicationId()).isEqualTo(applicationId);
    assertThat(detail.getLaaReference()).isEqualTo("DETAIL-" + applicationId);
    Instant createdAt =
        jdbcTemplate.queryForObject(
            "SELECT created_at FROM axon.application_current_state WHERE application_id = ?",
            Instant.class,
            applicationId);
    Instant submittedAt =
        jdbcTemplate.queryForObject(
            "SELECT submitted_at FROM axon.application_current_state WHERE application_id = ?",
            Instant.class,
            applicationId);
    assertThat(createdAt).isEqualTo(submittedAt);
    assertThat(detail.getCreatedAt().toInstant()).isEqualTo(createdAt);
    assertThat(detail.getSubmittedAt().toInstant()).isEqualTo(submittedAt);
    assertThat(detail.getClient().getFirstName()).isEqualTo("Ada");
    assertThat(detail.getClient().getLastName()).isEqualTo("Lovelace");
    assertThat(detail.getClient().getLastNameAtBirth()).isEqualTo("Byron");
    assertThat(detail.getClient().getDateOfBirth()).isEqualTo(LocalDate.of(1815, 12, 10));
    assertThat(detail.getClient().getHasNationalInsuranceNumber()).isTrue();
    assertThat(detail.getClient().getNationalInsuranceNumber()).isEqualTo("QQ123456C");
    assertThat(detail.getClient().getAppliedPreviously()).isFalse();
    assertThat(detail.getClient().getPreviousApplicationId()).isEqualTo("PREVIOUS-123");
    assertThat(detail.getClient().getRelationshipToInvolvedChildren()).isEqualTo("Parent");
    assertThat(detail.getClient().getAddresses())
        .singleElement()
        .satisfies(
            address -> {
              assertThat(address.getAddressLineOne()).isEqualTo("1 Analytical Engine Way");
              assertThat(address.getAddressLineTwo()).isEqualTo("Suite 2");
              assertThat(address.getAddressLineThree()).isEqualTo("Historic district");
              assertThat(address.getLocation()).isEqualTo("home");
              assertThat(address.getCity()).isEqualTo("London");
              assertThat(address.getCounty()).isEqualTo("Greater London");
              assertThat(address.getPostcode()).isEqualTo("SW1A 1AA");
              assertThat(address.getOrganisation()).isEqualTo("Analytical Engines Ltd");
              assertThat(address.getBuildingNumberName()).isEqualTo("Engine House");
              assertThat(address.getCountryCode()).isEqualTo("GBR");
              assertThat(address.getCountryName()).isEqualTo("United Kingdom");
              assertThat(address.getLookupUsed()).isTrue();
              assertThat(address.getCareOf()).isEqualTo("PERSON");
              assertThat(address.getCareOfFirstName()).isEqualTo("Augusta");
              assertThat(address.getCareOfLastName()).isEqualTo("King");
              assertThat(address.getCareOfOrganisationName()).isEqualTo("Engine House Ltd");
            });
    assertThat(detail.getProvider().getOfficeCode()).isEqualTo("1A001B");
    assertThat(detail.getProvider().getContactEmail()).isEqualTo("provider@example.com");
    assertThat(detail.getOpponents())
        .singleElement()
        .satisfies(
            opponent -> {
              assertThat(opponent.getFirstName()).isEqualTo("Rae");
              assertThat(opponent.getLastName()).isEqualTo("Opponent");
            });
    assertThat(detail.getProceedings())
        .extracting(
            proceeding -> proceeding.getMeaning(),
            proceeding -> proceeding.getMatterType(),
            proceeding -> proceeding.getMatterTypeCode())
        .containsExactly(
            tuple("Meaning one", "New matter", "MAT-001"),
            tuple("Meaning two", "Second matter", "MAT-002"));
    assertThat(detail.getProceedings().getFirst().getProceedingId()).isEqualTo(firstProceedingId);
    assertThat(detail.getProceedings().getFirst().getCode()).isEqualTo("SE003");
    assertThat(detail.getProceedings().getFirst().getCategoryOfLaw()).isEqualTo("New category");
    assertThat(detail.getProceedings().getFirst().getCategoryOfLawCode()).isEqualTo("CAT-001");
    assertThat(detail.getProceedings().getFirst().getDescription()).isEqualTo("Description one");
    assertThat(detail.getProceedings().getFirst().getClientInvolvementType())
        .isEqualTo("Respondent");
    assertThat(detail.getProceedings().getFirst().getClientInvolvementTypeCode()).isEqualTo("A");
    assertThat(detail.getProceedings().getFirst().getUsedDelegatedFunctions()).isFalse();
    assertThat(detail.getProceedings().getFirst().getSubstantiveLevelOfServiceCode()).isEqualTo(3);
    assertThat(detail.getProceedings().getFirst().getSubstantiveLevelOfServiceName())
        .isEqualTo("Full Representation");
    assertThat(detail.getProceedings().getFirst().getEmergencyLevelOfServiceCode()).isEqualTo(3);
    assertThat(detail.getProceedings().getFirst().getEmergencyLevelOfServiceName())
        .isEqualTo("Full Representation");
    assertThat(detail.getProceedings().getFirst().getSubstantiveCostLimitation())
        .isEqualByComparingTo(new BigDecimal("2500.25"));
    assertThat(detail.getProceedings().getFirst().getScopeLimitations())
        .singleElement()
        .satisfies(
            scope -> {
              assertThat(scope.getCode()).isEqualTo("FM062");
              assertThat(scope.getType()).isEqualTo("SUBSTANTIVE");
              assertThat(scope.getMeaning()).isEqualTo("Final hearing");
              assertThat(scope.getDescription())
                  .isEqualTo("Limited to all steps up to and including the final hearing");
            });
    assertThat(detail.getDecisionStatus()).isEqualTo(DecisionStatusResponse.PENDING);
    assertThat(detail.getProceedings().getFirst().getMeritsDecision())
        .isEqualTo(MeritsDecisionStatusResponse.PENDING);
  }

  @Test
  void givenDifferentContentAndEventTimes_whenListed_thenSortsByEventSubmittedAt() {
    String reference = "EVENT-TIME-" + UUID.randomUUID();
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    ApplicationCreateRequest first = validCreateApplicationRequest(firstId, UUID.randomUUID());
    ApplicationCreateRequest second = validCreateApplicationRequest(secondId, UUID.randomUUID());
    first.setLaaReference(reference);
    second.setLaaReference(reference);
    Map<String, Object> firstContent = new HashMap<>(first.getApplicationContent());
    Map<String, Object> secondContent = new HashMap<>(second.getApplicationContent());
    firstContent.put("submittedAt", "2099-01-01T00:00:00Z");
    secondContent.put("submittedAt", "2000-01-01T00:00:00Z");
    first.setApplicationContent(firstContent);
    second.setApplicationContent(secondContent);
    create(first);
    awaitApplicationProjection(firstId);
    create(second);
    awaitApplicationProjection(secondId);

    ResponseEntity<ApplicationSummaryResponse> response =
        restTemplate.exchange(
            "http://localhost:"
                + port
                + "/api/v0/applications?laaReference="
                + reference
                + "&sortBy=SUBMITTED_DATE&orderBy=ASC&pageSize=100",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationSummaryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getApplications())
        .extracting(ApplicationSummary::getApplicationId)
        .containsExactly(firstId, secondId);
    ApplicationResponse firstDetail = getApplication(firstId).getBody();
    ApplicationResponse secondDetail = getApplication(secondId).getBody();
    assertThat(response.getBody().getApplications())
        .extracting(ApplicationSummary::getSubmittedAt)
        .containsExactly(firstDetail.getSubmittedAt(), secondDetail.getSubmittedAt());
  }

  @Test
  void givenMatterTypeCodes_whenFiltered_thenMatchesLeadCodeExactly() {
    ApplicationCreateRequest matching =
        applicationWithMatterPairs(
            "Family", "CAT-123", "Shared display name", "MAT-123", "Other matter", "MAT-999");
    ApplicationCreateRequest nonMatching =
        applicationWithMatterPairs(
            "Family", "CAT-123", "Shared display name", "OTHER-CODE", "Other matter", "MAT-999");
    String reference = "CODE-FILTER-" + UUID.randomUUID();
    matching.setLaaReference(reference);
    nonMatching.setLaaReference(reference);
    create(matching);
    create(nonMatching);
    awaitApplicationProjection(matching.getId());
    awaitApplicationProjection(nonMatching.getId());

    ResponseEntity<ApplicationSummaryResponse> response =
        restTemplate.exchange(
            "http://localhost:"
                + port
                + "/api/v0/applications?laaReference="
                + reference
                + "&matterTypeCode=MAT-123&page=1&pageSize=10",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationSummaryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getPaging().getTotalRecords()).isEqualTo(1);
    assertThat(response.getBody().getPaging().getItemsReturned()).isEqualTo(1);
    assertThat(response.getBody().getApplications())
        .extracting(ApplicationSummary::getApplicationId)
        .containsExactly(matching.getId());
    assertThat(response.getBody().getApplications())
        .extracting(
            ApplicationSummary::getCategoryOfLaw,
            ApplicationSummary::getCategoryOfLawCode,
            ApplicationSummary::getMatterType,
            ApplicationSummary::getMatterTypeCode)
        .containsExactly(tuple("Family", "CAT-123", "Shared display name", "MAT-123"));
  }

  @Test
  void givenLinkedApplications_whenListedAndRead_thenReturnsClientNames() {
    UUID leadId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    String lastName = "Linked-" + UUID.randomUUID().toString().replace("-", "");
    ApplicationCreateRequest lead = applicationWithClient(leadId, lastName, "Ada");
    ApplicationCreateRequest member = applicationWithClient(memberId, lastName, "Grace");
    create(lead);
    create(member);
    awaitApplicationProjection(leadId);
    awaitApplicationProjection(memberId);
    ResponseEntity<Void> linked =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications/" + memberId + "/link",
            HttpMethod.POST,
            new HttpEntity<>(
                new ApplicationLinkRequest(leadId, ApplicationLinkType.FAMILY), headers()),
            Void.class);
    assertThat(linked.getStatusCode()).isIn(HttpStatus.NO_CONTENT, HttpStatus.ACCEPTED);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              ResponseEntity<ApplicationSummaryResponse> applications = listApplications(lastName);
              assertThat(applications.getBody().getApplications())
                  .extracting(ApplicationSummary::getApplicationId)
                  .contains(leadId, memberId);
              assertThat(findApplication(applications, leadId).getLinkedApplications()).hasSize(1);
            });
    ResponseEntity<ApplicationSummaryResponse> listed = listApplications(lastName);
    for (UUID applicationId : List.of(leadId, memberId)) {
      ApplicationSummary summary = findApplication(listed, applicationId);
      ApplicationResponse detail = getApplication(applicationId).getBody();
      assertThat(summary.getLinkedApplications())
          .singleElement()
          .satisfies(
              linkedMember -> {
                assertThat(linkedMember.getApplicationId()).isNotEqualTo(applicationId);
                assertThat(linkedMember.getLaaReference()).isEqualTo("LAA-123");
                assertThat(linkedMember.getClientFirstName())
                    .isEqualTo(applicationId.equals(leadId) ? "Grace" : "Ada");
                assertThat(linkedMember.getClientLastName()).isEqualTo(lastName);
              });
      assertThat(detail.getLinkedApplications())
          .singleElement()
          .satisfies(
              linkedMember -> {
                assertThat(linkedMember.getApplicationId()).isNotEqualTo(applicationId);
                assertThat(linkedMember.getClientFirstName())
                    .isEqualTo(applicationId.equals(leadId) ? "Grace" : "Ada");
                assertThat(linkedMember.getClientLastName()).isEqualTo(lastName);
              });
    }
  }

  @Test
  void givenCallerWithoutApplicationScope_whenGetApplications_thenReturnsForbidden() {
    HttpHeaders headers = headers();
    headers.setBearerAuth(TestJwtDecoderConfig.UNSCOPED_BEARER_TOKEN);

    ResponseEntity<String> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void givenCallerWithoutApplicationScope_whenGetApplicationNotes_thenReturnsForbidden() {
    HttpHeaders headers = headers();
    headers.setBearerAuth(TestJwtDecoderConfig.UNSCOPED_BEARER_TOKEN);

    ResponseEntity<String> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications/" + UUID.randomUUID() + "/notes",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void givenCivilManageAccounts_whenGetApplications_thenFiltersBeforePagingAndCounting() {
    String reference = "ACCESS-" + UUID.randomUUID();
    UUID officeA = createApplication("1A001B", reference);
    UUID officeB = createApplication("2B002C", reference);
    UUID officeC = createApplication("3C003D", reference);
    awaitApplicationProjection(officeA);
    awaitApplicationProjection(officeB);
    awaitApplicationProjection(officeC);

    ResponseEntity<ApplicationSummaryResponse> response =
        restTemplate.exchange(
            "http://localhost:"
                + port
                + "/api/v0/applications?laaReference="
                + reference
                + "&pageSize=1",
            HttpMethod.GET,
            new HttpEntity<>(civilManageHeaders(TestJwtDecoderConfig.OFFICE_A_AND_B_BEARER_TOKEN)),
            ApplicationSummaryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getPaging().getTotalRecords()).isEqualTo(2);
    assertThat(response.getBody().getApplications())
        .extracting(ApplicationSummary::getApplicationId)
        .containsAnyOf(officeA, officeB)
        .doesNotContain(officeC);
  }

  @Test
  void givenCivilManageCallerWithoutMatchingAccount_whenGetApplication_thenReturnsNotFound() {
    String reference = "ACCESS-" + UUID.randomUUID();
    UUID applicationId = createApplication("2B002C", reference);
    awaitApplicationProjection(applicationId);

    ResponseEntity<String> detail =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications/" + applicationId,
            HttpMethod.GET,
            new HttpEntity<>(civilManageHeaders(TestJwtDecoderConfig.OFFICE_A_BEARER_TOKEN)),
            String.class);
    ResponseEntity<ApplicationSummaryResponse> list =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications?laaReference=" + reference,
            HttpMethod.GET,
            new HttpEntity<>(civilManageHeaders(TestJwtDecoderConfig.OFFICE_A_BEARER_TOKEN)),
            ApplicationSummaryResponse.class);

    assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(list.getBody().getApplications()).isEmpty();
    assertThat(list.getBody().getPaging().getTotalRecords()).isZero();
  }

  @Test
  void givenCivilManageCallerWithoutAccounts_whenGetApplications_thenReturnsNoRows() {
    String reference = "ACCESS-" + UUID.randomUUID();
    UUID applicationId = createApplication("1A001B", reference);
    awaitApplicationProjection(applicationId);

    ResponseEntity<ApplicationSummaryResponse> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications?laaReference=" + reference,
            HttpMethod.GET,
            new HttpEntity<>(civilManageHeaders(TestJwtDecoderConfig.NO_ACCOUNTS_BEARER_TOKEN)),
            ApplicationSummaryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getApplications()).isEmpty();
    assertThat(response.getBody().getPaging().getTotalRecords()).isZero();
  }

  @Test
  void givenProviderOfficeChanges_whenProjectionsAdvance_thenCivilManageVisibilityChanges() {
    String reference = "ACCESS-" + UUID.randomUUID();
    UUID applicationId = createApplication("1A001B", reference);
    awaitApplicationProjection(applicationId);

    ResponseEntity<ApplicationResponse> initiallyVisible =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications/" + applicationId,
            HttpMethod.GET,
            new HttpEntity<>(civilManageHeaders(TestJwtDecoderConfig.OFFICE_A_BEARER_TOKEN)),
            ApplicationResponse.class);
    assertThat(initiallyVisible.getStatusCode()).isEqualTo(HttpStatus.OK);

    Map<String, Object> updatedContent =
        new HashMap<>(validApplicationContent(applicationId, UUID.randomUUID()));
    updatedContent.put(
        "provider", Map.of("officeCode", "2B002C", "contactEmail", "provider@example.com"));
    ResponseEntity<Void> update =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications/" + applicationId,
            HttpMethod.PATCH,
            new HttpEntity<>(new ApplicationUpdateRequest(updatedContent), headers()),
            Void.class);
    assertThat(update.getStatusCode()).isIn(HttpStatus.NO_CONTENT, HttpStatus.ACCEPTED);
    awaitApplicationProjectionVersion(applicationId, 1L);

    ResponseEntity<String> noLongerVisible =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications/" + applicationId,
            HttpMethod.GET,
            new HttpEntity<>(civilManageHeaders(TestJwtDecoderConfig.OFFICE_A_BEARER_TOKEN)),
            String.class);
    ResponseEntity<ApplicationResponse> visibleWithUpdatedOffice =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications/" + applicationId,
            HttpMethod.GET,
            new HttpEntity<>(civilManageHeaders(TestJwtDecoderConfig.OFFICE_A_AND_B_BEARER_TOKEN)),
            ApplicationResponse.class);

    assertThat(noLongerVisible.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(visibleWithUpdatedOffice.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void givenDraftAndSubmittedPriorAuthorities_whenGetApplications_thenReturnsLinkedSummaries() {
    UUID applicationId = grantedApplication();
    UUID draftId = saveDisbursementDraft(applicationId);
    UUID submittedId = saveDisbursementDraft(applicationId);
    awaitPriorAuthorityProjection(draftId);
    awaitPriorAuthorityProjection(submittedId);

    Instant originalCreatedAt =
        jdbcTemplate.queryForObject(
            "SELECT created_at FROM axon.prior_authority_current_state WHERE prior_authority_id = ?",
            Instant.class,
            submittedId);

    ResponseEntity<String> submitResponse =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/prior-authorities/" + submittedId + "/submit",
            new HttpEntity<>(null, headers()),
            String.class);
    assertThat(submitResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.ACCEPTED);
    awaitPriorAuthoritySubmitted(submittedId);

    ResponseEntity<ApplicationSummaryResponse> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications?pageSize=100",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationSummaryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ApplicationSummary application = findApplication(response, applicationId);

    assertThat(application.getPriorAuthorities())
        .extracting(
            PriorAuthoritySummary::getPriorAuthorityId,
            item -> item.getStatus().getValue(),
            PriorAuthoritySummary::getDecision)
        .containsExactlyInAnyOrder(
            tuple(draftId, "DRAFT", null), tuple(submittedId, "SUBMITTED", null));
    assertThat(application.getPriorAuthorities())
        .allSatisfy(summary -> assertThat(summary.getCreatedAt()).isNotNull());

    PriorAuthoritySummary submittedSummary =
        application.getPriorAuthorities().stream()
            .filter(summary -> summary.getPriorAuthorityId().equals(submittedId))
            .findFirst()
            .orElseThrow();
    assertThat(submittedSummary.getCreatedAt().toInstant()).isEqualTo(originalCreatedAt);
  }

  @Test
  void givenPriorAuthority_whenGetById_thenIncludesSummary() {
    UUID applicationId = grantedApplication();
    UUID priorAuthorityId = saveDisbursementDraft(applicationId);
    awaitPriorAuthorityProjection(priorAuthorityId);
    String storedPriorAuthorityType =
        jdbcTemplate.queryForObject(
            "SELECT payload #>> '{content,priorAuthorityType}' "
                + "FROM axon.prior_authority_draft WHERE prior_authority_id = ?",
            String.class,
            priorAuthorityId);
    assertThat(storedPriorAuthorityType).isEqualTo("DISBURSEMENT");

    ResponseEntity<ApplicationResponse> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications/" + applicationId,
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getPriorAuthorities())
        .singleElement()
        .satisfies(
            summary -> {
              assertThat(summary.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
              assertThat(summary.getStatus()).isEqualTo(PriorAuthoritySummary.StatusEnum.DRAFT);
              assertThat(summary.getPriorAuthorityType())
                  .isEqualTo(PriorAuthoritySummary.PriorAuthorityTypeEnum.DISBURSEMENT);
              assertThat(summary.getCreatedAt()).isNotNull();
            });
  }

  @Test
  void
      givenCreatedApplication_whenGetApplicationsFilteredByStatus_thenReturnsMatchingApplication() {
    UUID applicationId = UUID.randomUUID();
    createApplication(applicationId);
    awaitApplicationProjection(applicationId);

    ResponseEntity<ApplicationSummaryResponse> response =
        restTemplate.exchange(
            "http://localhost:"
                + port
                + "/api/v0/applications?status=APPLICATION_SUBMITTED&pageSize=100",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationSummaryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getApplications())
        .extracting(application -> application.getApplicationId())
        .contains(applicationId);
  }

  @Test
  void givenPriorAuthorityOnOneApplication_whenGetApplications_thenOtherApplicationHasNone() {
    UUID applicationWithPriorAuthority = grantedApplication();
    UUID applicationWithoutPriorAuthority = UUID.randomUUID();
    createApplication(applicationWithoutPriorAuthority);
    awaitApplicationProjection(applicationWithoutPriorAuthority);
    UUID draftId = saveDisbursementDraft(applicationWithPriorAuthority);
    awaitPriorAuthorityProjection(draftId);

    ResponseEntity<ApplicationSummaryResponse> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications?pageSize=100",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationSummaryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(findApplication(response, applicationWithPriorAuthority).getPriorAuthorities())
        .extracting(PriorAuthoritySummary::getPriorAuthorityId)
        .containsExactly(draftId);
    assertThat(findApplication(response, applicationWithoutPriorAuthority).getPriorAuthorities())
        .isEmpty();
  }

  private void createApplication(UUID applicationId) {
    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  private void create(ApplicationCreateRequest request) {
    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(request, headers()),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  private ApplicationCreateRequest applicationWithClient(
      UUID applicationId, String clientLastName, String firstName) {
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applicationId, UUID.randomUUID());
    Map<String, Object> content = new HashMap<>(request.getApplicationContent());
    Map<String, Object> client = new HashMap<>((Map<String, Object>) content.get("client"));
    client.put("firstName", firstName);
    client.put("lastName", clientLastName);
    content.put("client", client);
    request.setApplicationContent(content);
    return request;
  }

  private UUID createApplication(String officeCode, String laaReference) {
    UUID applicationId = UUID.randomUUID();
    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(
                    applicationId, UUID.randomUUID(), officeCode, laaReference),
                headers()),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return applicationId;
  }

  private ApplicationReadModel awaitApplicationProjection(UUID applicationId) {
    return await()
        .alias("application projection to be populated for " + applicationId)
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () ->
                queryGateway
                    .query(new FindApplicationByIdQuery(applicationId), ApplicationReadModel.class)
                    .join(),
            java.util.Objects::nonNull);
  }

  private ApplicationReadModel awaitApplicationProjectionVersion(UUID applicationId, long version) {
    return await()
        .alias("application projection to reach version " + version + " for " + applicationId)
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () ->
                queryGateway
                    .query(new FindApplicationByIdQuery(applicationId), ApplicationReadModel.class)
                    .join(),
            projected -> projected != null && projected.getApplicationDataVersion() == version);
  }

  private void grantApplication(UUID applicationId) {
    ResponseEntity<Void> response =
        restTemplate.exchange(
            "http://localhost:"
                + port
                + "/api/v0/applications/"
                + applicationId
                + "/auto-grant-outcome",
            HttpMethod.PATCH,
            new HttpEntity<>(
                new AutoGrantedOutcomeRequest(
                    AutoGrantOutcome.AUTOGRANTED, Map.of("certificateNumber", "PA-CERT-001")),
                headers()),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  private UUID grantedApplication() {
    UUID applicationId = UUID.randomUUID();
    createApplication(applicationId);
    awaitApplicationProjection(applicationId);
    grantApplication(applicationId);
    awaitApplicationProjectionVersion(applicationId, 1L);
    return applicationId;
  }

  private UUID saveDisbursementDraft(UUID applicationId) {
    CreatePriorAuthorityDraftRequest request =
        CreatePriorAuthorityDraftRequest.builder()
            .applicationId(applicationId)
            .priorAuthorityType(PriorAuthorityType.DISBURSEMENT)
            .justification("Interpreter costs for proceedings")
            .disbursementDetails(
                DisbursementDetails.builder()
                    .disbursementPurpose("Court interpreter")
                    .disbursementAmount(BigDecimal.valueOf(150))
                    .build())
            .build();
    ResponseEntity<SavePriorAuthorityDraftResponse> draftResponse =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/prior-authorities",
            new HttpEntity<>(request, headers()),
            SavePriorAuthorityDraftResponse.class);
    assertThat(draftResponse.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    return draftResponse.getBody().getPriorAuthorityId();
  }

  private PriorAuthorityResult awaitPriorAuthorityProjection(UUID priorAuthorityId) {
    return await()
        .alias("prior authority projection to be populated for " + priorAuthorityId)
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () ->
                queryGateway
                    .query(
                        new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
                        PriorAuthorityResult.class)
                    .join(),
            java.util.Objects::nonNull);
  }

  private PriorAuthorityResult awaitPriorAuthoritySubmitted(UUID priorAuthorityId) {
    return await()
        .alias("prior authority projection to reach SUBMITTED for " + priorAuthorityId)
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () ->
                queryGateway
                    .query(
                        new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
                        PriorAuthorityResult.class)
                    .join(),
            result -> result != null && "SUBMITTED".equals(result.status()));
  }

  private ApplicationSummary findApplication(
      ResponseEntity<ApplicationSummaryResponse> response, UUID applicationId) {
    return response.getBody().getApplications().stream()
        .filter(application -> application.getApplicationId().equals(applicationId))
        .findFirst()
        .orElseThrow();
  }

  private ResponseEntity<ApplicationSummaryResponse> listApplications(String lastName) {
    return restTemplate.exchange(
        "http://localhost:"
            + port
            + "/api/v0/applications?clientLastName="
            + lastName
            + "&pageSize=100",
        HttpMethod.GET,
        new HttpEntity<>(headers()),
        ApplicationSummaryResponse.class);
  }

  private ResponseEntity<ApplicationResponse> getApplication(UUID applicationId) {
    return restTemplate.exchange(
        "http://localhost:" + port + "/api/v0/applications/" + applicationId,
        HttpMethod.GET,
        new HttpEntity<>(headers()),
        ApplicationResponse.class);
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }

  private HttpHeaders civilManageHeaders(String bearerToken) {
    HttpHeaders headers = headers();
    headers.set("X-Service-Name", "CIVIL_MANAGE");
    headers.setBearerAuth(bearerToken);
    return headers;
  }
}

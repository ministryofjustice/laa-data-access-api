package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.AutoGrantedOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.BillingType;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.DecisionTimeRequested;
import uk.gov.justice.laa.dstew.access.model.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.ExpertCosts;
import uk.gov.justice.laa.dstew.access.model.ExpertDetails;
import uk.gov.justice.laa.dstew.access.model.ExpertMakePriorAuthorityDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MakePriorAuthorityDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse.DecisionEnum;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftResponse;
import uk.gov.justice.laa.dstew.access.model.TimeRequested;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignRequest;
import util.ProjectionAwaiter;

/** Full HTTP/Postgres/Axon integration tests for Prior Authority decision handling. */
class PriorAuthorityDecisionIntegrationTest extends BaseIntegrationTest {

  private static final OffsetDateTime DATE_GRANTED = OffsetDateTime.parse("2026-11-01T10:00:00Z");
  private static final String TIME_OVERRIDE_JSON = "{\"hours\":3,\"minutes\":15}";

  @Autowired private ObjectMapper objectMapper;

  @Autowired private QueryGateway queryGateway;

  private ProjectionAwaiter projectionAwaiter;

  @BeforeEach
  void initialiseProjectionAwaiter() {
    projectionAwaiter = new ProjectionAwaiter(queryGateway);
  }

  @Test
  void givenSubmittedDisbursement_whenGranted_thenPersistsDecision() {
    var priorAuthorityId = submittedAndAssigned(disbursementDraft());
    var request =
        decisionRequest(
            DecisionStatus.GRANTED, "Granted as per policy guidelines", BigDecimal.valueOf(150));

    assertThat(patchDecision(priorAuthorityId, request).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    assertDecisionPersisted(
        priorAuthorityId, DecisionEnum.GRANTED, "Granted as per policy guidelines");
  }

  @Test
  void givenSubmittedExpert_whenRefused_thenPersistsRefusal() {
    var priorAuthorityId = submittedAndAssigned(expertDraft(fixedRateCosts()));
    var request =
        decisionRequest(
            DecisionStatus.REFUSED, "Insufficient justification provided", BigDecimal.ZERO);

    assertThat(patchDecision(priorAuthorityId, request).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    assertDecisionPersisted(
        priorAuthorityId, DecisionEnum.REFUSED, "Insufficient justification provided");
  }

  @Test
  void givenUnsavedPriorAuthority_whenDecided_thenReturnsNotFound() {
    var request = decisionRequest(DecisionStatus.GRANTED, "Should not reach here", BigDecimal.ZERO);

    assertThat(patchDecision(UUID.randomUUID(), request).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenDecidedPriorAuthority_whenDecidedDifferently_thenReturnsConflict() {
    var priorAuthorityId = submittedAndAssigned(disbursementDraft());
    var grant =
        decisionRequest(
            DecisionStatus.GRANTED, "Granted as per policy guidelines", BigDecimal.valueOf(150));
    var refuse = decisionRequest(DecisionStatus.REFUSED, "Actually refused", BigDecimal.ZERO);

    assertThat(patchDecision(priorAuthorityId, grant).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(patchDecision(priorAuthorityId, refuse).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void givenHourlyExpert_whenDecisionWithTimeOverride_thenResponseShowsOverrideAndOriginal() {
    var priorAuthorityId = submittedAndAssigned(expertDraft(hourlyCosts()));
    var request =
        decisionRequest(DecisionStatus.GRANTED, "Granted", BigDecimal.valueOf(200))
            .expert(
                new ExpertMakePriorAuthorityDecisionRequest()
                    .grantedTime(DecisionTimeRequested.builder().hours(3).minutes(15).build()));

    assertThat(patchDecision(priorAuthorityId, request).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    projectionAwaiter.awaitDecidedPriorAuthority(priorAuthorityId);
    var response = getPriorAuthority(priorAuthorityId);
    var grantedTime = response.getDecisionDetails().getExpert().getGrantedTime();
    assertThat(grantedTime.getHours()).isEqualTo(3);
    assertThat(grantedTime.getMinutes()).isEqualTo(15);
    assertOriginalTimeRequested(response);
  }

  @Test
  void givenHourlyExpert_whenDecisionWithoutTimeOverride_thenOriginalTimeUnchanged() {
    var priorAuthorityId = submittedAndAssigned(expertDraft(hourlyCosts()));
    var request = decisionRequest(DecisionStatus.GRANTED, "Granted", BigDecimal.valueOf(200));

    assertThat(patchDecision(priorAuthorityId, request).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    projectionAwaiter.awaitDecidedPriorAuthority(priorAuthorityId);
    var response = getPriorAuthority(priorAuthorityId);
    assertThat(response.getDecisionDetails().getExpert()).isNull();
    assertOriginalTimeRequested(response);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"hours\":-1,\"minutes\":0}",
        "{\"hours\":1,\"minutes\":60}",
        "{\"hours\":1,\"minutes\":-1}",
        "{\"hours\":2.5,\"minutes\":0}",
        "{\"hours\":\"2\",\"minutes\":0}",
        "{\"hours\":1}"
      })
  void givenInvalidTimeOverride_whenDecision_thenReturnsBadRequestProblemDetail(String json) {
    var priorAuthorityId = submittedAndAssigned(expertDraft(hourlyCosts()));

    var response = patchDecision(priorAuthorityId, decisionJson(json));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("Generic Validation Error", "grantedTime");
    assertThat(decisionDataVersionCount(priorAuthorityId)).isZero();
  }

  @Test
  void givenFixedRateExpert_whenDecisionWithTimeOverride_thenReturnsBadRequest() {
    var priorAuthorityId = submittedAndAssigned(expertDraft(fixedRateCosts()));

    var response = patchDecision(priorAuthorityId, decisionJson(TIME_OVERRIDE_JSON));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .contains(
            "expert.grantedTime is only permitted for EXPERT prior authorities with HOURLY billing");
    assertThat(decisionDataVersionCount(priorAuthorityId)).isZero();
  }

  @Test
  void givenDisbursement_whenDecisionWithTimeOverride_thenReturnsBadRequest() {
    var priorAuthorityId = submittedAndAssigned(disbursementDraft());

    var response = patchDecision(priorAuthorityId, decisionJson(TIME_OVERRIDE_JSON));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(decisionDataVersionCount(priorAuthorityId)).isZero();
  }

  private void assertDecisionPersisted(
      UUID priorAuthorityId, DecisionEnum decision, String justification) {
    assertThat(decisionDataVersionCount(priorAuthorityId)).isOne();
    projectionAwaiter.awaitDecidedPriorAuthority(priorAuthorityId);
    var priorAuthority = getPriorAuthority(priorAuthorityId);
    assertThat(priorAuthority.getStatus()).isEqualTo(PriorAuthorityResponse.StatusEnum.DECIDED);
    assertThat(priorAuthority.getDecision()).isEqualTo(decision);
    assertThat(priorAuthority.getDecisionJustification()).isEqualTo(justification);
  }

  private void assertOriginalTimeRequested(PriorAuthorityResponse response) {
    var timeRequested = response.getExpertDetails().getExpertCosts().getTimeRequested();
    assertThat(timeRequested.getHours()).isEqualTo(2);
    assertThat(timeRequested.getMinutes()).isEqualTo(30);
  }

  private Integer decisionDataVersionCount(UUID priorAuthorityId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM axon.prior_authority_data"
            + " WHERE prior_authority_id = ? AND data_version > 0",
        Integer.class,
        priorAuthorityId);
  }

  private MakePriorAuthorityDecisionRequest decisionRequest(
      DecisionStatus decision, String justification, BigDecimal amountGranted) {
    return new MakePriorAuthorityDecisionRequest()
        .decision(decision)
        .decisionJustification(justification)
        .amountGranted(amountGranted)
        .dateGranted(DATE_GRANTED)
        .eventHistory(new EventHistoryRequest())
        .priorAuthorityVersion(0L);
  }

  private String decisionJson(String grantedTimeJson) {
    return """
        {"decision":"GRANTED","decisionJustification":"Granted","amountGranted":200,
         "dateGranted":"2026-11-01T10:00:00Z","eventHistory":{},"priorAuthorityVersion":0,
         "expert":{"grantedTime":%s}}
        """
        .formatted(grantedTimeJson);
  }

  private ResponseEntity<String> patchDecision(UUID priorAuthorityId, Object body) {
    return restTemplate.exchange(
        priorAuthorityUrl(priorAuthorityId) + "/decision",
        HttpMethod.PATCH,
        new HttpEntity<>(body, headers()),
        String.class);
  }

  private PriorAuthorityResponse getPriorAuthority(UUID priorAuthorityId) {
    var response =
        restTemplate.exchange(
            priorAuthorityUrl(priorAuthorityId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return objectMapper.readValue(response.getBody(), PriorAuthorityResponse.class);
  }

  private UUID submittedAndAssigned(CreatePriorAuthorityDraftRequest draft) {
    draft.setApplicationId(grantedApplication());
    var saveResponse =
        restTemplate.postForEntity(
            url("/api/v0/prior-authorities"), new HttpEntity<>(draft, headers()), String.class);
    assertThat(saveResponse.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    var priorAuthorityId =
        objectMapper
            .readValue(saveResponse.getBody(), SavePriorAuthorityDraftResponse.class)
            .getPriorAuthorityId();
    projectionAwaiter.awaitPriorAuthority(priorAuthorityId);

    var submitResponse =
        restTemplate.postForEntity(
            priorAuthorityUrl(priorAuthorityId) + "/submit",
            new HttpEntity<>(null, headers()),
            String.class);
    assertThat(submitResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.ACCEPTED);
    projectionAwaiter.awaitPriorAuthority(priorAuthorityId);

    var assignResponse =
        restTemplate.exchange(
            url("/api/v0/work-list/" + priorAuthorityId + "/assign"),
            HttpMethod.POST,
            new HttpEntity<>(new WorkListAssignRequest(0L), headers()),
            Void.class);
    assertThat(assignResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    return priorAuthorityId;
  }

  private CreatePriorAuthorityDraftRequest disbursementDraft() {
    return CreatePriorAuthorityDraftRequest.builder()
        .priorAuthorityType(PriorAuthorityType.DISBURSEMENT)
        .justification("Interpreter costs for proceedings")
        .disbursementDetails(
            DisbursementDetails.builder()
                .disbursementPurpose("Court interpreter")
                .disbursementAmount(BigDecimal.valueOf(150))
                .build())
        .build();
  }

  private CreatePriorAuthorityDraftRequest expertDraft(ExpertCosts costs) {
    return CreatePriorAuthorityDraftRequest.builder()
        .priorAuthorityType(PriorAuthorityType.EXPERT)
        .justification("Expert witness for case")
        .expertDetails(
            ExpertDetails.builder()
                .expertType("Forensic Accountant")
                .expertFullName("Jane Smith")
                .expertPostcode("SW1A 1AA")
                .expertCosts(costs)
                .build())
        .build();
  }

  private ExpertCosts fixedRateCosts() {
    return ExpertCosts.builder()
        .billingType(BillingType.FIXED_RATE)
        .totalAmount(BigDecimal.valueOf(1500))
        .costsSharedWithOtherParties(false)
        .build();
  }

  private ExpertCosts hourlyCosts() {
    return ExpertCosts.builder()
        .billingType(BillingType.HOURLY)
        .hourlyRate(BigDecimal.valueOf(100))
        .timeRequested(TimeRequested.builder().hours(2).minutes(30).build())
        .totalAmount(BigDecimal.valueOf(250))
        .costsSharedWithOtherParties(false)
        .build();
  }

  private UUID grantedApplication() {
    var applicationId = UUID.randomUUID();
    var createResponse =
        restTemplate.postForEntity(
            url("/api/v0/applications"),
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()),
            Void.class);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    projectionAwaiter.awaitApplication(applicationId);

    var grantResponse =
        restTemplate.exchange(
            url("/api/v0/applications/" + applicationId + "/auto-grant-outcome"),
            HttpMethod.PATCH,
            new HttpEntity<>(
                new AutoGrantedOutcomeRequest(
                    AutoGrantOutcome.AUTOGRANTED, Map.of("certificateNumber", "PA-CERT-001")),
                headers()),
            Void.class);
    assertThat(grantResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    projectionAwaiter.awaitApplicationVersion(applicationId, 1L);
    return applicationId;
  }

  private String priorAuthorityUrl(UUID priorAuthorityId) {
    return url("/api/v0/prior-authorities/" + priorAuthorityId);
  }
}

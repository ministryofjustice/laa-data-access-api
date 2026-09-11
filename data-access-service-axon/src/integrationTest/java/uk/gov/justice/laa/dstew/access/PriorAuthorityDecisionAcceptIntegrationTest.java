package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.time.OffsetDateTime;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.AutoGrantedOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.BillingType;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.ExpertCosts;
import uk.gov.justice.laa.dstew.access.model.ExpertDetails;
import uk.gov.justice.laa.dstew.access.model.MakePriorAuthorityDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftResponse;
import uk.gov.justice.laa.dstew.access.model.SubmitPriorAuthorityDraftResponse;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.FindPriorAuthorityByPriorAuthorityIdQuery;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

/** Full HTTP/Postgres/Axon integration tests for Prior Authority decision handling. */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"feature.enable-dev-token=true"})
@AutoConfigureTestRestTemplate
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class PriorAuthorityDecisionAcceptIntegrationTest {
  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private QueryGateway queryGateway;

  @Test
  void givenSubmittedPriorAuthority_whenMakePriorAuthorityDecisionGranted_thenPersistsDecision()
      throws Exception {
    UUID applicationId = grantedApplication();
    UUID priorAuthorityId =
        saveDraftAndSubmit(
            applicationId,
            PriorAuthorityType.DISBURSEMENT,
            "Interpreter costs for proceedings",
            validDisbursementRequest());

    OffsetDateTime decidedAt = OffsetDateTime.now();
    MakePriorAuthorityDecisionRequest decisionRequest =
        new MakePriorAuthorityDecisionRequest()
            .decision(DecisionStatus.GRANTED)
            .decisionJustification("Granted as per policy guidelines")
            .amountGranted(150.0)
            .dateGranted(decidedAt)
            .eventHistory(new EventHistoryRequest())
            .priorAuthorityVersion(0L);

    ResponseEntity<Void> decisionResponse =
        restTemplate.exchange(
            decisionUrl(priorAuthorityId),
            HttpMethod.PATCH,
            new HttpEntity<>(decisionRequest, headers()),
            Void.class);

    assertThat(decisionResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_data"
                    + " WHERE prior_authority_id = ? AND data_version > 0",
                Integer.class,
                priorAuthorityId))
        .isEqualTo(1);

    awaitPriorAuthorityProjection(priorAuthorityId);
    ResponseEntity<String> getResponse =
        restTemplate.exchange(
            priorAuthorityUrl(priorAuthorityId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    PriorAuthorityResponse priorAuthority =
        objectMapper.readValue(getResponse.getBody(), PriorAuthorityResponse.class);
    assertThat(priorAuthority.getDecision()).isEqualTo(PriorAuthorityResponse.DecisionEnum.GRANTED);
    assertThat(priorAuthority.getDecisionJustification())
        .isEqualTo("Granted as per policy guidelines");
  }

  @Test
  void givenSubmittedPriorAuthority_whenMakePriorAuthorityDecisionRefused_thenPersistsRefusal()
      throws Exception {
    UUID applicationId = grantedApplication();
    UUID priorAuthorityId =
        saveDraftAndSubmit(
            applicationId,
            PriorAuthorityType.EXPERT,
            "Expert witness for case",
            validExpertDetails());

    OffsetDateTime decidedAt = OffsetDateTime.now();
    MakePriorAuthorityDecisionRequest decisionRequest =
        new MakePriorAuthorityDecisionRequest()
            .decision(DecisionStatus.REFUSED)
            .decisionJustification("Insufficient justification provided")
            .amountGranted(0.0)
            .dateGranted(decidedAt)
            .eventHistory(new EventHistoryRequest())
            .priorAuthorityVersion(0L);

    ResponseEntity<Void> decisionResponse =
        restTemplate.exchange(
            decisionUrl(priorAuthorityId),
            HttpMethod.PATCH,
            new HttpEntity<>(decisionRequest, headers()),
            Void.class);

    assertThat(decisionResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_data"
                    + " WHERE prior_authority_id = ? AND data_version > 0",
                Integer.class,
                priorAuthorityId))
        .isEqualTo(1);

    awaitPriorAuthorityProjection(priorAuthorityId);
    ResponseEntity<String> getResponse =
        restTemplate.exchange(
            priorAuthorityUrl(priorAuthorityId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);
    PriorAuthorityResponse priorAuthority =
        objectMapper.readValue(getResponse.getBody(), PriorAuthorityResponse.class);
    assertThat(priorAuthority.getDecision()).isEqualTo(PriorAuthorityResponse.DecisionEnum.REFUSED);
    assertThat(priorAuthority.getDecisionJustification())
        .isEqualTo("Insufficient justification provided");
  }

  @Test
  void givenUnsavedPriorAuthority_whenMakePriorAuthorityDecision_thenReturnsNotFound() {
    UUID nonexistentPriorAuthorityId = UUID.randomUUID();
    OffsetDateTime decidedAt = OffsetDateTime.now();
    MakePriorAuthorityDecisionRequest decisionRequest =
        new MakePriorAuthorityDecisionRequest()
            .decision(DecisionStatus.GRANTED)
            .decisionJustification("Should not reach here")
            .amountGranted(0.0)
            .dateGranted(decidedAt)
            .eventHistory(new EventHistoryRequest())
            .priorAuthorityVersion(0L);

    ResponseEntity<Void> decisionResponse =
        restTemplate.exchange(
            decisionUrl(nonexistentPriorAuthorityId),
            HttpMethod.PATCH,
            new HttpEntity<>(decisionRequest, headers()),
            Void.class);

    assertThat(decisionResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenDecisionAlreadyRecorded_whenMakePriorAuthorityDecisionWithDifferentRequest_thenReturnsConflict()
      throws Exception {
    UUID applicationId = grantedApplication();
    UUID priorAuthorityId =
        saveDraftAndSubmit(
            applicationId,
            PriorAuthorityType.DISBURSEMENT,
            "Interpreter costs for proceedings",
            validDisbursementRequest());

    OffsetDateTime decidedAt = OffsetDateTime.now();
    MakePriorAuthorityDecisionRequest firstDecisionRequest =
        new MakePriorAuthorityDecisionRequest()
            .decision(DecisionStatus.GRANTED)
            .decisionJustification("Granted as per policy guidelines")
            .amountGranted(150.0)
            .dateGranted(decidedAt)
            .eventHistory(new EventHistoryRequest())
            .priorAuthorityVersion(0L);

    ResponseEntity<Void> firstDecision =
        restTemplate.exchange(
            decisionUrl(priorAuthorityId),
            HttpMethod.PATCH,
            new HttpEntity<>(firstDecisionRequest, headers()),
            Void.class);
    assertThat(firstDecision.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    MakePriorAuthorityDecisionRequest secondDecisionRequest =
        new MakePriorAuthorityDecisionRequest()
            .decision(DecisionStatus.REFUSED)
            .decisionJustification("Actually refused")
            .amountGranted(0.0)
            .dateGranted(decidedAt)
            .eventHistory(new EventHistoryRequest())
            .priorAuthorityVersion(0L);

    ResponseEntity<Void> secondDecision =
        restTemplate.exchange(
            decisionUrl(priorAuthorityId),
            HttpMethod.PATCH,
            new HttpEntity<>(secondDecisionRequest, headers()),
            Void.class);
    assertThat(secondDecision.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  private UUID saveDraftAndSubmit(
      UUID applicationId,
      PriorAuthorityType priorAuthorityType,
      String justification,
      DisbursementDetails disbursement)
      throws Exception {
    return saveDraftAndSubmit(applicationId, priorAuthorityType, justification, disbursement, null);
  }

  private UUID saveDraftAndSubmit(
      UUID applicationId,
      PriorAuthorityType priorAuthorityType,
      String justification,
      ExpertDetails expert)
      throws Exception {
    return saveDraftAndSubmit(applicationId, priorAuthorityType, justification, null, expert);
  }

  private UUID saveDraftAndSubmit(
      UUID applicationId,
      PriorAuthorityType priorAuthorityType,
      String justification,
      DisbursementDetails disbursement,
      ExpertDetails expert)
      throws Exception {
    CreatePriorAuthorityDraftRequest request =
        CreatePriorAuthorityDraftRequest.builder()
            .applicationId(applicationId)
            .priorAuthorityType(priorAuthorityType)
            .justification(justification)
            .disbursementDetails(disbursement)
            .expertDetails(expert)
            .build();
    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);
    assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    UUID priorAuthorityId =
        objectMapper
            .readValue(response.getBody(), SavePriorAuthorityDraftResponse.class)
            .getPriorAuthorityId();
    awaitPriorAuthorityProjection(priorAuthorityId);

    ResponseEntity<String> submitResponse =
        restTemplate.postForEntity(
            submitUrl(priorAuthorityId), new HttpEntity<>(null, headers()), String.class);
    assertThat(submitResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.ACCEPTED);
    objectMapper.readValue(submitResponse.getBody(), SubmitPriorAuthorityDraftResponse.class);
    awaitPriorAuthorityProjection(priorAuthorityId);

    return priorAuthorityId;
  }

  private DisbursementDetails validDisbursementRequest() {
    return DisbursementDetails.builder()
        .disbursementPurpose("Court interpreter")
        .disbursementAmount(150.0)
        .build();
  }

  private ExpertDetails validExpertDetails() {
    return ExpertDetails.builder()
        .expertType("Forensic Accountant")
        .expertFullName("Jane Smith")
        .expertPostcode("SW1A 1AA")
        .expertCosts(
            ExpertCosts.builder()
                .billingType(BillingType.FIXED_RATE)
                .totalAmount(1500.0)
                .costsSharedWithOtherParties(false)
                .build())
        .build();
  }

  private void createApplication(UUID applicationId, UUID applyProceedingId) {
    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, applyProceedingId), headers()),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  private UUID grantedApplication() {
    UUID applicationId = UUID.randomUUID();
    createApplication(applicationId, UUID.randomUUID());
    awaitApplicationProjection(applicationId);
    grantApplication(applicationId);
    awaitApplicationProjectionVersion(applicationId, 1L);
    return applicationId;
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

  private String saveDraftUrl() {
    return "http://localhost:" + port + "/api/v0/prior-authorities";
  }

  private String priorAuthorityUrl(UUID priorAuthorityId) {
    return "http://localhost:" + port + "/api/v0/prior-authorities/" + priorAuthorityId;
  }

  private String submitUrl(UUID priorAuthorityId) {
    return priorAuthorityUrl(priorAuthorityId) + "/submit";
  }

  private String decisionUrl(UUID priorAuthorityId) {
    return priorAuthorityUrl(priorAuthorityId) + "/decision";
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }
}

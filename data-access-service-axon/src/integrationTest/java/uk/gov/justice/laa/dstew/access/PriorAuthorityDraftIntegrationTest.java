package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

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
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.AutoGrantedOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.DisbursementDetails;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftResponse;
import uk.gov.justice.laa.dstew.access.model.SubmitPriorAuthorityDraftResponse;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.FindPriorAuthorityByPriorAuthorityIdQuery;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

/** Full HTTP/Postgres/Axon integration tests for the Prior Authority draft/submit lifecycle. */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"feature.enable-dev-token=true"})
@AutoConfigureTestRestTemplate
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PriorAuthorityDraftIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private QueryGateway queryGateway;

  @Test
  void givenGrantedApplication_whenSavePriorAuthorityDraft_thenPersistsDraftAndProjectsInProgress()
      throws Exception {
    UUID applicationId = grantedApplication();
    CreatePriorAuthorityDraftRequest request =
        CreatePriorAuthorityDraftRequest.builder()
            .applicationId(applicationId)
            .priorAuthorityType(PriorAuthorityType.EXPERT)
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    SavePriorAuthorityDraftResponse body =
        objectMapper.readValue(response.getBody(), SavePriorAuthorityDraftResponse.class);
    UUID priorAuthorityId = body.getPriorAuthorityId();
    assertThat(priorAuthorityId).isNotNull();
    assertThat(body.getSavedAt()).isNotNull();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT application_id FROM axon.prior_authority_draft WHERE prior_authority_id = ?",
                UUID.class,
                priorAuthorityId))
        .isEqualTo(applicationId);
    awaitPriorAuthorityProjection(priorAuthorityId);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_current_state WHERE prior_authority_id = ?",
                Integer.class,
                priorAuthorityId))
        .isEqualTo(1);

    ResponseEntity<String> draftResponse =
        restTemplate.exchange(
            priorAuthorityUrl(priorAuthorityId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);
    assertThat(draftResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    PriorAuthorityResponse draft =
        objectMapper.readValue(draftResponse.getBody(), PriorAuthorityResponse.class);
    assertThat(draft.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(draft.getApplicationId()).isEqualTo(applicationId);
    assertThat(draft.getStatus()).isNull();
    assertThat(draft.getPriorAuthorityType())
        .isEqualTo(PriorAuthorityResponse.PriorAuthorityTypeEnum.EXPERT);
  }

  @Test
  void givenMissingApplication_whenSavePriorAuthorityDraft_thenReturnsNotFound() {
    UUID nonexistentApplicationId = UUID.randomUUID();
    CreatePriorAuthorityDraftRequest request =
        CreatePriorAuthorityDraftRequest.builder()
            .applicationId(nonexistentApplicationId)
            .priorAuthorityType(PriorAuthorityType.EXPERT)
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_draft WHERE application_id = ?",
                Integer.class,
                nonexistentApplicationId))
        .isZero();
  }

  @Test
  void givenUngrantedApplication_whenSavePriorAuthorityDraft_thenReturnsBadRequest()
      throws Exception {
    UUID applicationId = UUID.randomUUID();
    createApplication(applicationId, UUID.randomUUID());
    awaitApplicationProjection(applicationId);
    CreatePriorAuthorityDraftRequest request =
        CreatePriorAuthorityDraftRequest.builder()
            .applicationId(applicationId)
            .priorAuthorityType(PriorAuthorityType.EXPERT)
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("GRANTED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_draft WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isZero();
  }

  @Test
  void givenExistingDraft_whenUpdatePriorAuthorityDraft_thenReturns204AndPersistsUpdatedContent()
      throws Exception {
    UUID applicationId = grantedApplication();
    UUID priorAuthorityId = saveDraft(applicationId, PriorAuthorityType.EXPERT, null, null);

    SavePriorAuthorityDraftRequest updateRequest =
        SavePriorAuthorityDraftRequest.builder()
            .priorAuthorityType(PriorAuthorityType.EXPERT)
            .justification("Updated justification")
            .build();
    ResponseEntity<Void> updateResponse =
        restTemplate.exchange(
            priorAuthorityUrl(priorAuthorityId),
            HttpMethod.PUT,
            new HttpEntity<>(updateRequest, headers()),
            Void.class);

    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<String> draftResponse =
        restTemplate.exchange(
            priorAuthorityUrl(priorAuthorityId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);
    PriorAuthorityResponse draft =
        objectMapper.readValue(draftResponse.getBody(), PriorAuthorityResponse.class);
    assertThat(draft.getJustification()).isEqualTo("Updated justification");
  }

  @Test
  void givenDraftInProgress_whenSubmitPriorAuthorityDraft_thenTransitionsToPendingAndDeletesDraft()
      throws Exception {
    UUID applicationId = grantedApplication();
    UUID priorAuthorityId =
        saveDraft(
            applicationId,
            PriorAuthorityType.DISBURSEMENT,
            "Interpreter costs for proceedings",
            validDisbursementRequest());

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            submitUrl(priorAuthorityId), new HttpEntity<>(null, headers()), String.class);

    assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    SubmitPriorAuthorityDraftResponse body =
        objectMapper.readValue(response.getBody(), SubmitPriorAuthorityDraftResponse.class);
    assertThat(body.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(body.getSubmittedAt()).isNotNull();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM axon.prior_authority_current_state WHERE prior_authority_id = ?",
                String.class,
                priorAuthorityId))
        .isEqualTo("PENDING");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_data"
                    + " WHERE prior_authority_id = ? AND data_version = 0",
                Integer.class,
                priorAuthorityId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_draft WHERE prior_authority_id = ?",
                Integer.class,
                priorAuthorityId))
        .isZero();

    ResponseEntity<String> getResponse =
        restTemplate.exchange(
            priorAuthorityUrl(priorAuthorityId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    PriorAuthorityResponse priorAuthority =
        objectMapper.readValue(getResponse.getBody(), PriorAuthorityResponse.class);
    assertThat(priorAuthority.getStatus()).isEqualTo("PENDING");
    assertThat(priorAuthority.getDisbursementDetails().getDisbursementPurpose())
        .isEqualTo("Court interpreter");
  }

  @Test
  void
      givenDraftViolatesSchema_whenSubmitPriorAuthorityDraft_thenReturnsBadRequestAndDraftPersists()
          throws Exception {
    UUID applicationId = grantedApplication();
    // Missing justification, which the full PriorAuthority schema requires at submit time.
    UUID priorAuthorityId = saveDraft(applicationId, PriorAuthorityType.EXPERT, null, null);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            submitUrl(priorAuthorityId), new HttpEntity<>(null, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_draft WHERE prior_authority_id = ?",
                Integer.class,
                priorAuthorityId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_current_state WHERE prior_authority_id = ?",
                Integer.class,
                priorAuthorityId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.prior_authority_data WHERE prior_authority_id = ?",
                Integer.class,
                priorAuthorityId))
        .isZero();
  }

  @Test
  void givenNoDraftInProgress_whenSubmitPriorAuthorityDraft_thenReturnsNotFound() {
    UUID nonexistentPriorAuthorityId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            submitUrl(nonexistentPriorAuthorityId),
            new HttpEntity<>(null, headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenNoDraft_whenGetPriorAuthority_thenReturnsNotFound() {
    UUID nonexistentPriorAuthorityId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.exchange(
            priorAuthorityUrl(nonexistentPriorAuthorityId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private UUID saveDraft(
      UUID applicationId,
      PriorAuthorityType priorAuthorityType,
      String justification,
      DisbursementDetails disbursement)
      throws Exception {
    CreatePriorAuthorityDraftRequest request =
        CreatePriorAuthorityDraftRequest.builder()
            .applicationId(applicationId)
            .priorAuthorityType(priorAuthorityType)
            .justification(justification)
            .disbursementDetails(disbursement)
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
    return priorAuthorityId;
  }

  private DisbursementDetails validDisbursementRequest() {
    return DisbursementDetails.builder()
        .disbursementPurpose("Court interpreter")
        .disbursementAmount(150.0)
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

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }
}

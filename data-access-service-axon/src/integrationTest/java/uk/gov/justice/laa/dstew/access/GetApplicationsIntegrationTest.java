package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.time.Instant;
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
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.AutoGrantedOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.DisbursementDetails;
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
            "http://localhost:" + port + "/api/v0/applications",
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
            "http://localhost:" + port + "/api/v0/applications",
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
  void
      givenCreatedApplication_whenGetApplicationsFilteredByStatus_thenReturnsMatchingApplication() {
    UUID applicationId = UUID.randomUUID();
    createApplication(applicationId);
    awaitApplicationProjection(applicationId);

    ResponseEntity<ApplicationSummaryResponse> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications?status=APPLICATION_SUBMITTED",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationSummaryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getApplications())
        .extracting(application -> application.getApplicationId())
        .contains(applicationId);
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
                    .disbursementAmount(150.0)
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

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }
}

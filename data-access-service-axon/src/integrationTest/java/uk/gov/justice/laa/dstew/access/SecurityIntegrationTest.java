package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
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
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "feature.disable-security=false",
      "feature.enable-dev-token=true",
      "ENTRA_ISSUER_URI=https://issuer.example.test",
      "ENTRA_JWK_SET_URI=https://issuer.example.test/jwks",
      "ENTRA_AUD=api://data-access-api-test"
    })
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SecurityIntegrationTest {

  private static final String CASEWORKER_TOKEN = "swagger-caseworker-token";
  private static final String UNKNOWN_TOKEN = "unknown-token";

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void givenNoUser_whenCallingSecuredEndpoints_thenReturnsUnauthorized() {
    UUID applicationId = UUID.randomUUID();

    assertUnauthorized(HttpMethod.GET, "/api/v0/caseworkers", null);
    assertUnauthorized(HttpMethod.GET, "/api/v0/applications", null);
    assertUnauthorized(HttpMethod.GET, "/api/v0/prior-authorities/" + applicationId, null);
    assertUnauthorized(HttpMethod.GET, "/api/v0/individuals", null);
    assertUnauthorized(
        HttpMethod.POST,
        "/api/v0/applications",
        validCreateApplicationRequest(UUID.randomUUID(), UUID.randomUUID()));
    assertUnauthorized(HttpMethod.GET, "/api/v0/applications/" + applicationId, null);
    assertUnauthorized(
        HttpMethod.GET, "/api/v0/applications/" + applicationId + "/certificate", null);
    assertUnauthorized(
        HttpMethod.GET, "/api/v0/applications/" + applicationId + "/history-search", null);
    assertUnauthorized(HttpMethod.PATCH, "/api/v0/applications/" + applicationId, updateRequest());
    assertUnauthorized(
        HttpMethod.PATCH, "/api/v0/applications/" + applicationId + "/decision", decisionRequest());
    assertUnauthorized(
        HttpMethod.PATCH,
        "/api/v0/applications/" + applicationId + "/auto-grant-outcome",
        autoGrantOutcomeRequest());
    assertUnauthorized(
        HttpMethod.POST, "/api/v0/applications/" + applicationId + "/notes", noteRequest());
    assertUnauthorized(HttpMethod.GET, "/api/v0/applications/" + applicationId + "/notes", null);
    assertUnauthorized(HttpMethod.POST, "/api/v0/applications/assign", assignRequest());
    assertUnauthorized(
        HttpMethod.POST, "/api/v0/applications/" + applicationId + "/unassign", unassignRequest());
  }

  @Test
  void givenUnknownToken_whenCallingSecuredEndpoints_thenReturnsForbidden() {
    UUID applicationId = UUID.randomUUID();

    assertForbidden(HttpMethod.GET, "/api/v0/caseworkers", null);
    assertForbidden(HttpMethod.GET, "/api/v0/applications", null);
    assertForbidden(HttpMethod.GET, "/api/v0/individuals", null);
    assertForbidden(
        HttpMethod.POST,
        "/api/v0/applications",
        validCreateApplicationRequest(UUID.randomUUID(), UUID.randomUUID()));
    assertForbidden(HttpMethod.GET, "/api/v0/applications/" + applicationId, null);
    assertForbidden(HttpMethod.GET, "/api/v0/applications/" + applicationId + "/certificate", null);
    assertForbidden(
        HttpMethod.GET, "/api/v0/applications/" + applicationId + "/history-search", null);
    assertForbidden(HttpMethod.PATCH, "/api/v0/applications/" + applicationId, updateRequest());
    assertForbidden(
        HttpMethod.PATCH, "/api/v0/applications/" + applicationId + "/decision", decisionRequest());
    assertForbidden(
        HttpMethod.PATCH,
        "/api/v0/applications/" + applicationId + "/auto-grant-outcome",
        autoGrantOutcomeRequest());
    assertForbidden(
        HttpMethod.POST, "/api/v0/applications/" + applicationId + "/notes", noteRequest());
    assertForbidden(HttpMethod.GET, "/api/v0/applications/" + applicationId + "/notes", null);
    assertForbidden(HttpMethod.POST, "/api/v0/applications/assign", assignRequest());
    assertForbidden(
        HttpMethod.POST, "/api/v0/applications/" + applicationId + "/unassign", unassignRequest());
  }

  @Test
  void givenCaseworkerDevToken_whenGetUnknownPriorAuthority_thenReturnsNotFound() {
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO axon.caseworkers (id, username) VALUES (?, ?)", firstId, "alice@example.com");
    jdbcTemplate.update(
        "INSERT INTO axon.caseworkers (id, username) VALUES (?, ?)", secondId, "bob@example.com");

    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.setBearerAuth(CASEWORKER_TOKEN);

    ResponseEntity<List<Map<String, Object>>> response =
        exchangeCaseworkers(new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .extracting(item -> item.get("username"))
        .contains("alice@example.com", "bob@example.com");

    ResponseEntity<String> priorAuthorityResponse =
        exchangeString(
            HttpMethod.GET,
            "/api/v0/prior-authorities/" + UUID.randomUUID(),
            new HttpEntity<>(headers));

    assertThat(priorAuthorityResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private ApplicationUpdateRequest updateRequest() {
    ApplicationCreateRequest submitted =
        validCreateApplicationRequest(UUID.randomUUID(), UUID.randomUUID());
    return new ApplicationUpdateRequest()
        .status(ApplicationStatus.APPLICATION_SUBMITTED)
        .applicationContent(submitted.getApplicationContent());
  }

  private MakeDecisionRequest decisionRequest() {
    return MakeDecisionRequest.builder()
        .applicationVersion(0L)
        .overallDecision(DecisionStatus.REFUSED)
        .autoGranted(false)
        .eventHistory(EventHistoryRequest.builder().eventDescription("decision").build())
        .proceedings(
            List.of(
                MakeDecisionProceedingRequest.builder()
                    .proceedingId(UUID.randomUUID())
                    .meritsDecision(
                        MeritsDecisionDetailsRequest.builder()
                            .decision(MeritsDecisionStatus.REFUSED)
                            .reason("reason")
                            .justification("justification")
                            .build())
                    .build()))
        .build();
  }

  private ManualOutcomeRequest autoGrantOutcomeRequest() {
    return new ManualOutcomeRequest(AutoGrantOutcome.MANUAL);
  }

  private CreateNoteRequest noteRequest() {
    return new CreateNoteRequest("security test note");
  }

  private CaseworkerAssignRequest assignRequest() {
    return new CaseworkerAssignRequest()
        .caseworkerId(UUID.randomUUID())
        .applicationIds(List.of(UUID.randomUUID()))
        .eventHistory(EventHistoryRequest.builder().eventDescription("assign").build());
  }

  private CaseworkerUnassignRequest unassignRequest() {
    return new CaseworkerUnassignRequest()
        .eventHistory(EventHistoryRequest.builder().eventDescription("unassign").build());
  }

  private void assertUnauthorized(HttpMethod method, String path, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    ResponseEntity<String> response = exchangeString(method, path, new HttpEntity<>(body, headers));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private void assertForbidden(HttpMethod method, String path, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.setBearerAuth(UNKNOWN_TOKEN);
    ResponseEntity<String> response = exchangeString(method, path, new HttpEntity<>(body, headers));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  private ResponseEntity<String> exchangeString(
      HttpMethod method, String path, HttpEntity<?> entity) {
    return restTemplate.exchange("http://localhost:" + port + path, method, entity, String.class);
  }

  private <T> ResponseEntity<T> exchangeCaseworkers(
      HttpEntity<?> entity, ParameterizedTypeReference<T> responseType) {
    return restTemplate.exchange(
        "http://localhost:" + port + "/api/v0/caseworkers", HttpMethod.GET, entity, responseType);
  }
}

package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadCommand;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationEvolve;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDraftRequest;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

/**
 * Contract test: verifies that events stored in {@code domain_event_entry} can be deserialised with
 * a plain Jackson {@code ObjectMapper} and that replaying them through {@link ApplicationEvolve}
 * reconstructs the correct {@link ApplicationState}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RawReplayContractTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private CommandGateway commandGateway;

  @Test
  void readsCompleteThinDraftAndSubmittedHistoryFromPostgres() {
    UUID applicationId = UUID.randomUUID();
    var request = validCreateApplicationRequest(applicationId, UUID.randomUUID());
    var draft =
        new CreateApplicationDraftRequest()
            .id(applicationId)
            .status(request.getStatus())
            .laaReference(request.getLaaReference())
            .applicationContent(request.getApplicationContent());
    var created =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/application-drafts",
            new HttpEntity<>(draft, headers()),
            Void.class);
    assertThat(created.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    String url = "http://localhost:" + port + "/api/v0/applications/" + applicationId + "/events";
    var draftEvents =
        restTemplate.exchange(
            url,
            org.springframework.http.HttpMethod.GET,
            new HttpEntity<>(headers()),
            uk.gov.justice.laa.dstew.access.model.AggregateEventResponse[].class);
    assertThat(draftEvents.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(draftEvents.getBody()).isNotEmpty();
    assertThat(draftEvents.getBody()[0].getEventType()).contains("ApplicationDraftStartedEvent");

    HttpHeaders providerHeaders = headers();
    providerHeaders.setBearerAuth(TestJwtDecoderConfig.OFFICE_C_BEARER_TOKEN);
    var providerDraft =
        restTemplate.exchange(
            url,
            org.springframework.http.HttpMethod.GET,
            new HttpEntity<>(providerHeaders),
            String.class);
    assertThat(providerDraft.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    commandGateway.sendAndWait(new SubmitApplicationDraftCommand(applicationId, Instant.now()));
    var events =
        restTemplate.exchange(
            url,
            org.springframework.http.HttpMethod.GET,
            new HttpEntity<>(headers()),
            uk.gov.justice.laa.dstew.access.model.AggregateEventResponse[].class);
    assertThat(events.getStatusCode()).isEqualTo(HttpStatus.OK);
    var storedIds =
        jdbcTemplate.queryForList(
            "SELECT event_identifier FROM axon.domain_event_entry "
                + "WHERE aggregate_identifier = ? ORDER BY sequence_number",
            String.class,
            applicationId.toString());
    assertThat(events.getBody())
        .extracting(uk.gov.justice.laa.dstew.access.model.AggregateEventResponse::getEventId)
        .containsExactlyElementsOf(storedIds);
    assertThat(events.getBody())
        .allSatisfy(
            event -> assertThat(event.getPayload()).doesNotContainKey("applicationContent"));
    var separateStream =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/prior-authorities/" + applicationId + "/events",
            org.springframework.http.HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);
    assertThat(separateStream.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    for (String path :
        List.of(
            "/api/v0/applications/" + UUID.randomUUID() + "/events",
            "/api/v0/prior-authorities/" + UUID.randomUUID() + "/events")) {
      String missingUrl = "http://localhost:" + port + path;
      var missing =
          restTemplate.exchange(
              missingUrl,
              org.springframework.http.HttpMethod.GET,
              new HttpEntity<>(headers()),
              String.class);
      assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      var unauthenticated = restTemplate.getForEntity(missingUrl, String.class);
      assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      HttpHeaders unscopedHeaders = headers();
      unscopedHeaders.setBearerAuth(TestJwtDecoderConfig.UNSCOPED_BEARER_TOKEN);
      var unscoped =
          restTemplate.exchange(
              missingUrl,
              org.springframework.http.HttpMethod.GET,
              new HttpEntity<>(unscopedHeaders),
              String.class);
      assertThat(unscoped.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
  }

  @Test
  void givenUploadedDocument_whenReplayedFromPostgres_thenRestoresMetadataAndDataVersion()
      throws Exception {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    var request = validCreateApplicationRequest(applicationId, UUID.randomUUID());
    var draft =
        new CreateApplicationDraftRequest()
            .id(applicationId)
            .status(request.getStatus())
            .laaReference(request.getLaaReference())
            .applicationContent(request.getApplicationContent());
    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/application-drafts",
            new HttpEntity<>(draft, headers()),
            Void.class);
    assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    commandGateway.sendAndWait(
        new ApplicationDocumentUploadCommand(
            applicationId,
            documentId,
            "GATEWAY_EVIDENCE",
            Instant.parse("2026-09-28T10:00:00Z"),
            12L,
            "application/pdf",
            "checksum",
            "CIVIL_APPLY",
            "client-report.pdf"));
    commandGateway.sendAndWait(new SubmitApplicationDraftCommand(applicationId, Instant.now()));
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT payload, payload_type FROM axon.domain_event_entry "
                + "WHERE aggregate_identifier = ? ORDER BY sequence_number",
            applicationId.toString());

    ApplicationState state = replayWithoutAxon(rows);

    assertThat(state.getApplicationDataVersion()).isZero();
    assertThat(state.getApplicationVersion()).isZero();
    assertThat(state.getUploadedDocuments())
        .extracting(DocumentMetadata::documentId)
        .containsExactly(documentId);
    assertThat(
            new String(
                (byte[]) rows.getLast().get("payload"), java.nio.charset.StandardCharsets.UTF_8))
        .doesNotContain("client-report.pdf");
  }

  @Test
  void givenCreatedApplication_whenEventsReplayedFromJdbcWithoutAxon_thenStateMatchesOriginal()
      throws Exception {
    UUID applicationId = UUID.randomUUID();
    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    // Poll until the event is persisted (projection may be async but event storage is synchronous).
    List<Map<String, Object>> rows =
        org.awaitility.Awaitility.await()
            .atMost(10, java.util.concurrent.TimeUnit.SECONDS)
            .until(
                () ->
                    jdbcTemplate.queryForList(
                        "SELECT payload, payload_type FROM axon.domain_event_entry"
                            + " WHERE aggregate_identifier = ? ORDER BY sequence_number",
                        applicationId.toString()),
                list -> !list.isEmpty());

    // Replay via plain JDBC + Jackson, with no Axon API involved.
    ApplicationState state = replayWithoutAxon(rows);

    assertThat(state.getApplicationId()).isEqualTo(applicationId);
    assertThat(state.getApplicationVersion()).isEqualTo(0L);
    assertThat(state.getApplicationDataVersion()).isEqualTo(0L);
    assertThat(state.getRequestFingerprint()).isNotNull().isNotEmpty();
  }

  // ── raw replay ─────────────────────────────────────────────────────────────────

  /** Replays events from the supplied JDBC rows into an {@link ApplicationState}. */
  private ApplicationState replayWithoutAxon(List<Map<String, Object>> rows) throws Exception {
    ApplicationState state = new ApplicationState();
    for (Map<String, Object> row : rows) {
      String payloadType = (String) row.get("payload_type");
      byte[] payload = (byte[]) row.get("payload");
      dispatchEvent(state, payloadType, payload);
    }
    return state;
  }

  private void dispatchEvent(ApplicationState state, String payloadType, byte[] payload)
      throws Exception {
    switch (payloadType) {
      case "uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent" ->
          ApplicationEvolve.apply(
              state, objectMapper.readValue(payload, ApplicationDraftStartedEvent.class));
      case "uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent" ->
          ApplicationEvolve.apply(
              state, objectMapper.readValue(payload, ApplicationCreatedEvent.class));
      case "uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent" ->
          ApplicationEvolve.apply(
              state, objectMapper.readValue(payload, ApplicationDecisionMadeEvent.class));
      case "uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent" ->
          ApplicationEvolve.apply(state, objectMapper.readValue(payload, NoteCreatedEvent.class));
      case "uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent" ->
          ApplicationEvolve.apply(
              state, objectMapper.readValue(payload, ApplicationDocumentUploadedEvent.class));
      default -> throw new IllegalArgumentException("Unknown event type: " + payloadType);
    }
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }
}

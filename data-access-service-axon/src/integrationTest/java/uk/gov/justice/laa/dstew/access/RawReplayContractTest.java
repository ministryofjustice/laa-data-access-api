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
import uk.gov.justice.laa.dstew.access.command.application.ApplicationEvolve;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationLinkedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupRequested;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
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
      case "uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent" ->
          ApplicationEvolve.apply(
              state, objectMapper.readValue(payload, ApplicationCreatedEvent.class));
      case "uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent" ->
          ApplicationEvolve.apply(
              state, objectMapper.readValue(payload, ApplicationDecisionMadeEvent.class));
      case "uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent" ->
          ApplicationEvolve.apply(state, objectMapper.readValue(payload, NoteCreatedEvent.class));
      case "uk.gov.justice.laa.dstew.access.command.application.ApplicationLinkedEvent" ->
          ApplicationEvolve.apply(
              state, objectMapper.readValue(payload, ApplicationLinkedEvent.class));
      case "uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupRequested" ->
          ApplicationEvolve.apply(
              state, objectMapper.readValue(payload, LinkedApplicationGroupRequested.class));
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

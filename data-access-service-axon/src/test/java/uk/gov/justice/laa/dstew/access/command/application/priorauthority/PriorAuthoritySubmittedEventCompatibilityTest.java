package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Event-compatibility check for {@link PriorAuthoritySubmittedEvent}. The event gained an additive
 * {@code applicationDataVersion} pointer. Events persisted before that field existed must still
 * deserialize, with the absent value defined as {@code 0L} — the parent application's original data
 * version, which always exists — so the work-list projection stays deterministic on replay.
 *
 * <p>See {@code docs/event-evolution.md} (Safe additive change).
 */
class PriorAuthoritySubmittedEventCompatibilityTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void givenOldJsonWithoutApplicationDataVersion_whenDeserialised_thenDefaultsToZero() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    String oldJson =
        """
        {
          "priorAuthorityId": "%s",
          "applicationId": "%s",
          "priorAuthorityType": "EXPERT",
          "schemaVersion": 1,
          "dataVersion": 0,
          "occurredAt": "2026-08-01T10:00:00Z"
        }
        """
            .formatted(priorAuthorityId, applicationId);

    PriorAuthoritySubmittedEvent event =
        objectMapper.readValue(oldJson, PriorAuthoritySubmittedEvent.class);

    assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(event.applicationId()).isEqualTo(applicationId);
    assertThat(event.priorAuthorityType()).isEqualTo("EXPERT");
    assertThat(event.schemaVersion()).isEqualTo(1);
    assertThat(event.dataVersion()).isZero();
    assertThat(event.applicationDataVersion()).isZero();
    assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-08-01T10:00:00Z"));
  }

  @Test
  void givenCurrentEvent_whenRoundTripped_thenPreservesApplicationDataVersion() {
    PriorAuthoritySubmittedEvent original =
        new PriorAuthoritySubmittedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "EXPERT",
            1,
            0L,
            5L,
            Instant.parse("2026-08-02T10:00:00Z"));

    PriorAuthoritySubmittedEvent roundTripped =
        objectMapper.readValue(
            objectMapper.writeValueAsString(original), PriorAuthoritySubmittedEvent.class);

    assertThat(roundTripped).isEqualTo(original);
  }
}

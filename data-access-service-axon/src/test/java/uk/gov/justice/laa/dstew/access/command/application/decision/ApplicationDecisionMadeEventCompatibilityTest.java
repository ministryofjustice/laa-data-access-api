package uk.gov.justice.laa.dstew.access.command.application.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

/** Verifies that persisted decision events remain readable after removing their actor field. */
class ApplicationDecisionMadeEventCompatibilityTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "76510f68-e974-4b36-a8e1-3879a87cf45e")
  void givenHistoricalJsonWithCaseworkerId_whenDeserialised_thenPreservesDecisionFields(
      String caseworkerId) {
    UUID applicationId = UUID.fromString("7ccf4f9e-f086-479f-b413-35f857b27fa8");
    String encodedCaseworkerId = caseworkerId == null ? "null" : "\"" + caseworkerId + "\"";
    String oldJson =
        """
        {
          "applicationId": "%s",
          "applicationVersion": 3,
          "applicationDataVersion": 5,
          "overallDecision": "GRANTED",
          "autoGranted": null,
          "caseworkerId": %s,
          "occurredAt": "2026-08-02T10:00:00Z"
        }
        """
            .formatted(applicationId, encodedCaseworkerId);

    ApplicationDecisionMadeEvent event =
        objectMapper.readValue(oldJson, ApplicationDecisionMadeEvent.class);

    assertThat(event.applicationId()).isEqualTo(applicationId);
    assertThat(event.applicationVersion()).isEqualTo(3L);
    assertThat(event.applicationDataVersion()).isEqualTo(5L);
    assertThat(event.overallDecision()).isEqualTo("GRANTED");
    assertThat(event.autoGranted()).isNull();
    assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-08-02T10:00:00Z"));
  }
}

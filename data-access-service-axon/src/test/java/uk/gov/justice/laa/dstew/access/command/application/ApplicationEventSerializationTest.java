package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;

class ApplicationEventSerializationTest {

  private final JsonMapper objectMapper =
      JsonMapper.builder().addModule(new JavaTimeModule()).build();

  @Test
  void givenAllFields_whenSerialised_thenRoundTripSucceeds() throws Exception {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T10:00:00Z");

    assertThat(
            roundTrip(
                new ApplicationCreatedEvent(
                    applicationId,
                    0L,
                    "hash",
                    "APPLICATION_SUBMITTED",
                    1,
                    "APPLY",
                    applicationId,
                    occurredAt,
                    null,
                    List.of())))
        .isEqualTo(
            new ApplicationCreatedEvent(
                applicationId,
                0L,
                "hash",
                "APPLICATION_SUBMITTED",
                1,
                "APPLY",
                applicationId,
                occurredAt,
                null,
                List.of()));
    assertThat(
            roundTrip(
                new ApplicationDecisionMadeEvent(
                    applicationId, 1L, 1L, "GRANTED", false, occurredAt)))
        .isEqualTo(
            new ApplicationDecisionMadeEvent(applicationId, 1L, 1L, "GRANTED", false, occurredAt));
    assertThat(
            roundTrip(
                new ApplicationAssignedToCaseworkerEvent(
                    applicationId, 1L, 1L, UUID.randomUUID(), occurredAt)))
        .isInstanceOf(ApplicationAssignedToCaseworkerEvent.class);
    assertThat(
            roundTrip(
                new ApplicationUnassignedFromCaseworkerEvent(applicationId, 2L, 2L, occurredAt)))
        .isEqualTo(new ApplicationUnassignedFromCaseworkerEvent(applicationId, 2L, 2L, occurredAt));
    assertThat(
            roundTrip(
                new ApplicationPiiRedactedEvent(applicationId, 3L, "gdpr", "tester", occurredAt)))
        .isEqualTo(
            new ApplicationPiiRedactedEvent(applicationId, 3L, "gdpr", "tester", occurredAt));
  }

  @Test
  void givenLegacyJsonWithoutRemovedFields_whenDeserialised_thenSucceeds() throws Exception {
    ApplicationCreatedEvent created =
        objectMapper.readValue(
            """
            {
              "applicationId":"%s",
              "applicationDataVersion":0,
              "requestFingerprint":"hash",
              "status":"APPLICATION_SUBMITTED",
              "schemaVersion":1,
              "applicationType":"APPLY",
              "applyApplicationId":"%s",
              "occurredAt":"2026-07-20T10:00:00Z",
              "leadApplicationId":null,
              "associatedApplicationIds":[]
            }
            """.formatted(UUID.randomUUID(), UUID.randomUUID()),
            ApplicationCreatedEvent.class);
    ApplicationDecisionMadeEvent decision =
        objectMapper.readValue(
            """
            {
              "applicationId":"%s",
              "applicationVersion":1,
              "applicationDataVersion":1,
              "overallDecision":"GRANTED",
              "autoGranted":false,
              "occurredAt":"2026-07-20T10:00:00Z"
            }
            """.formatted(UUID.randomUUID()),
            ApplicationDecisionMadeEvent.class);

    assertThat(created.associatedApplicationIds()).isEmpty();
    assertThat(decision.overallDecision()).isEqualTo("GRANTED");
  }

  @SuppressWarnings("unchecked")
  private <T> T roundTrip(T event) throws Exception {
    return (T) objectMapper.readValue(objectMapper.writeValueAsBytes(event), event.getClass());
  }
}

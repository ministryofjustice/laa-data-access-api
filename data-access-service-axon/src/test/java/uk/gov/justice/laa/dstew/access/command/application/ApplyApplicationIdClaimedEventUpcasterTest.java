package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationFinalisationDetails;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventhandling.EventData;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.upcasting.event.InitialEventRepresentation;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.junit.jupiter.api.Test;

class ApplyApplicationIdClaimedEventUpcasterTest {

  @Test
  void
      givenUnrevisionedClaimEvent_whenReplayed_thenMovesNestedEventFieldsIntoFinalisationDetails() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Serializer serializer = JacksonSerializer.defaultSerializer();
    String oldPayload = oldPayload(applyApplicationId, applicationId);
    EventData<String> eventData = eventData(oldPayload);
    IntermediateEventRepresentation initial = new InitialEventRepresentation(eventData, serializer);

    IntermediateEventRepresentation upcasted =
        new ApplyApplicationIdClaimedEventUpcaster()
            .upcast(java.util.stream.Stream.of(initial))
            .findFirst()
            .orElseThrow();
    ApplyApplicationIdClaimedEvent event = serializer.deserialize(upcasted.getData(String.class));

    assertThat(upcasted.getType().getRevision()).isEqualTo("1");
    assertThat(event)
        .isEqualTo(
            new ApplyApplicationIdClaimedEvent(
                applyApplicationId,
                applicationId,
                applicationFinalisationDetails(applyApplicationId),
                null));
  }

  private EventData<String> eventData(String payload) {
    SerializedObject<String> serializedPayload =
        new SimpleSerializedObject<>(
            payload, String.class, ApplyApplicationIdClaimedEvent.class.getName(), null);
    SerializedObject<String> serializedMetaData =
        new SimpleSerializedObject<>("{}", String.class, MetaData.class.getName(), null);
    return new EventData<>() {
      @Override
      public String getEventIdentifier() {
        return UUID.randomUUID().toString();
      }

      @Override
      public Instant getTimestamp() {
        return Instant.parse("2026-07-15T08:00:00Z");
      }

      @Override
      public SerializedObject<String> getMetaData() {
        return serializedMetaData;
      }

      @Override
      public SerializedObject<String> getPayload() {
        return serializedPayload;
      }
    };
  }

  private String oldPayload(UUID applyApplicationId, UUID applicationId) {
    return """
        {
          "applyApplicationId": "%s",
          "applicationId": "%s",
          "applicationCreatedEvent": {
            "applicationId": "%s",
            "status": "APPLICATION_SUBMITTED",
            "laaReference": "LAA-123",
            "applicationContent": null,
            "individuals": [],
            "schemaVersion": 1,
            "applicationType": "APPLY",
            "applyApplicationId": "%s",
            "submittedAt": "2026-07-14T12:30:00Z",
            "officeCode": "1A001B",
            "usedDelegatedFunctions": false,
            "categoryOfLaw": null,
            "matterType": null,
            "proceedings": [],
            "serialisedRequest": "{}",
            "occurredAt": "2026-07-15T08:00:00Z"
          },
          "leadApplicationId": null
        }
        """
        .formatted(applyApplicationId, applicationId, applicationId, applyApplicationId);
  }
}

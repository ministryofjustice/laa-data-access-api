package uk.gov.justice.laa.dstew.access.model;

import java.time.Instant;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.entity.dynamo.EventDynamoEntity;

public record Event(String eventType, UUID eventId, Instant timestamp, String description) {

  public static Event fromDynamoEntity(EventDynamoEntity entity) {
    if (entity == null) {
      return null;
    }
    return new Event(
        entity.getType(),
        UUID.fromString(entity.getPk().split("#")[1]),
        Instant.parse(entity.getSk()),
        entity.getDescription()
    );
  }
}

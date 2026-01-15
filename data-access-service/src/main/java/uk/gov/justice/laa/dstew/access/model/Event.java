package uk.gov.justice.laa.dstew.access.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.entity.dynamo.EventDynamoEntity;

@Builder
public record Event(String eventType, String eventId, Instant timestamp, String description) {

  public static Event fromDynamoEntity(EventDynamoEntity entity) {
    if (entity == null) {
      return null;
    }
    return new Event(
        entity.getType(),
        entity.getPk().split("#")[1],
        Instant.parse(entity.getSk()),
        entity.getDescription()
    );
  }
}

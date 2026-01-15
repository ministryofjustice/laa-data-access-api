package uk.gov.justice.laa.dstew.access.spike;

import java.time.Instant;
import lombok.Builder;
import uk.gov.justice.laa.dstew.access.model.Caseworker;
import uk.gov.justice.laa.dstew.access.spike.dynamo.DomainEventDynamoDB;

@Builder
public record Event(EventType eventType, String eventId, Instant timestamp, String description, String caseworkerId) {

  public static Event fromDynamoEntity(DomainEventDynamoDB entity) {
    if (entity == null) {
      return null;
    }
    return Event.builder()
        .eventType(EventType.valueOf(entity.getType()))
        .eventId(extractEventIdFromPk(entity.getPk()))
        .timestamp(Instant.parse(entity.getCreatedAt()))
        .caseworkerId(entity.getCaseworkerId())
        .description(entity.getDescription())
        .build();
  }

  private static String extractEventIdFromPk(String pk) {
    return pk != null && pk.contains("#") ? pk.split("#", 2)[1] : pk;
  }
}

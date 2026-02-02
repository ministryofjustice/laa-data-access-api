package uk.gov.justice.laa.dstew.access.spike;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.spike.dynamo.DomainEventDynamoDB;

@Builder
public record Event(DomainEventType eventType, String applicationId, Instant timestamp, String description, String caseworkerId,
                    java.util.UUID domainEventId) {

  public static Event fromDynamoEntity(DomainEventDynamoDB entity) {
    if (entity == null) {
      return null;
    }
    return Event.builder()
        .eventType(DomainEventType.valueOf(entity.getType()))
        .applicationId(extractEventIdFromPk(entity.getPk()))
        .timestamp(Instant.parse(entity.getCreatedAt()))
        .caseworkerId(entity.getCaseworkerId())
        .description(entity.getDescription())
        .build();
  }

  public static Event fromAttributeValueMap(Map<String, AttributeValue> map) {
    if (map == null || map.isEmpty()) {
      return null;
    }
    return Event.builder()
        .eventType(DomainEventType.valueOf(map.get("type").s()))
        .applicationId(extractEventIdFromPk(map.get("pk").s()))
        .timestamp(Instant.parse(map.get("createdAt").s()))
        .caseworkerId(map.get("caseworkerId") != null ? map.get("caseworkerId").s() : null)
        .description(map.get("description") != null ? map.get("description").s() : null)
        .build();
  }

  private static String extractEventIdFromPk(String pk) {
    return pk != null && pk.contains("#") ? pk.split("#", 2)[1] : pk;
  }
}

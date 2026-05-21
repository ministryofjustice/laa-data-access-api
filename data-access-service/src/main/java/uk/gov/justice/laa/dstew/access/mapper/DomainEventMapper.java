package uk.gov.justice.laa.dstew.access.mapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEventResponse;

/** Maps between domain event entity and domain event API model. */
@Mapper(componentModel = "spring")
public interface DomainEventMapper {
  /**
   * Converts a {@link DomainEventEntity} to an API-facing {@link ApplicationDomainEventResponse}
   * model. Safely handles nulls: if the {@code entity} itself is null, the method returns {@code
   * null}.
   *
   * @param entity the {@link DomainEventEntity} to map (might be null)
   * @return a new {@link ApplicationDomainEventResponse} object populated with mapped values
   */
  default ApplicationDomainEventResponse toDomainEvent(DomainEventEntity entity) {
    if (entity == null) {
      return null;
    }
    return ApplicationDomainEventResponse.builder()
        .applicationId(entity.getApplicationId())
        .caseworkerId(entity.getCaseworkerId())
        .domainEventType(entity.getType())
        .eventDescription(extractEventDescription(entity.getData()))
        .createdAt(OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC))
        .createdBy(entity.getCreatedBy())
        .build();
  }

  /**
   * Extracts the {@code eventDescription} string value from a JSON data field.
   *
   * <p>Returns {@code null} if the input is null, blank, or does not contain an {@code
   * eventDescription} key.
   *
   * @param json the raw JSON string stored in the domain event data field
   * @return the extracted event description, or {@code null}
   * @throws IllegalArgumentException if the {@code json} cannot be parsed
   */
  default String extractEventDescription(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      JsonNode node = MapperUtil.getObjectMapper().readTree(json);
      JsonNode descriptionNode = node.get("eventDescription");
      if (descriptionNode == null || descriptionNode.isNull()) {
        return null;
      }
      return descriptionNode.textValue();
    } catch (JacksonException e) {
      throw new IllegalArgumentException(
          String.format("Failed to parse domain event data field as JSON: %s", e.getMessage()));
    }
  }
}

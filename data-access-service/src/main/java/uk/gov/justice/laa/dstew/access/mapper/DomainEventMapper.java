package uk.gov.justice.laa.dstew.access.mapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEventResponse;
import uk.gov.justice.laa.dstew.access.model.AssignApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.DomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.UnassignApplicationDomainEventDetails;

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
        .eventDescription(deserialiseEventDescription(entity.getData(), entity.getType()))
        .createdAt(OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC))
        .createdBy(entity.getCreatedBy())
        .build();
  }

  /**
   * Deserialises the JSON data field into the appropriate typed details class based on the domain
   * event type, then returns the {@code eventDescription} from it.
   *
   * <p>Returns {@code null} if {@code data} is null, blank, or the event type has no {@code
   * eventDescription}.
   *
   * @param data the raw JSON string stored in the domain event data field
   * @param eventType the type of the domain event
   * @return the extracted event description, or {@code null}
   */
  default String deserialiseEventDescription(String data, DomainEventType eventType) {
    if (data == null || data.isBlank() || eventType == null) {
      return null;
    }
    return switch (eventType) {
      case ASSIGN_APPLICATION_TO_CASEWORKER ->
          getEventDescription(data, AssignApplicationDomainEventDetails.class);
      case UNASSIGN_APPLICATION_TO_CASEWORKER ->
          getEventDescription(data, UnassignApplicationDomainEventDetails.class);
      case APPLICATION_MAKE_DECISION_GRANTED, APPLICATION_MAKE_DECISION_REFUSED ->
          getEventDescription(data, MakeDecisionDomainEventDetails.class);
      default -> null;
    };
  }

  private static String getEventDescription(
      String data, Class<? extends DomainEventDetails> detailsClass) {
    try {
      return MapperUtil.getObjectMapper().readValue(data, detailsClass).getEventDescription();
    } catch (Exception e) {
      return null;
    }
  }
}

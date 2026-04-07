package uk.gov.justice.laa.dstew.access.mapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEventResponse;

/**
 * Maps between domain event entity and domain event API model.
 */
@Mapper(componentModel = "spring")
public interface DomainEventMapper {
  /**
   * Converts a {@link DomainEventEntity} to an API-facing {@link ApplicationDomainEventResponse} model.
   * Safely handles nulls: if the {@code entity} itself is null,
   * the method returns {@code null}.
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
                      .eventDescription(entity.getData())
                      .createdAt(OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC))
                      .createdBy(entity.getCreatedBy())
                      .build();
  }
}

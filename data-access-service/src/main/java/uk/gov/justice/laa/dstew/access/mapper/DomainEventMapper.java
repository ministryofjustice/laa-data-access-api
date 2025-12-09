package uk.gov.justice.laa.dstew.access.mapper;

import org.mapstruct.Mapper;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.DomainEvent;

/**
 * Maps between domain event entity and domain event API model.
 */
@Mapper(componentModel = "spring")
public class DomainEventMapper {

  private final OffsetInstantMapper instantMapper = new OffsetInstantMapperImpl();

  DomainEvent toDomainEvent(DomainEventEntity entity) {
    return DomainEvent.builder()
                      .applicationId(entity.getApplicationId())
                      .caseworkerId(entity.getCaseworkerId())
                      .domainEventType(entity.getType())
                      .eventDescription(getEventDescription(entity))
                      .createdAt(instantMapper.toOffsetDateTime(entity.getCreatedAt()))
                      .createdBy(entity.getCreatedBy())
                      .build();
  }

  private String getEventDescription(DomainEventEntity entity) {
    return (String) entity.getData().getOrDefault("eventDescription", null);
  }
}

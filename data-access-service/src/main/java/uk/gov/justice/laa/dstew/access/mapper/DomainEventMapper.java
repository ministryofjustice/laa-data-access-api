package uk.gov.justice.laa.dstew.access.mapper;

import org.mapstruct.Mapper;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.DomainEvent;
import uk.gov.justice.laa.dstew.access.model.Individual;

/**
 * Maps between domain event entity and domain event API model.
 */
@Mapper(componentModel = "spring")
public interface DomainEventMapper {

  OffsetInstantMapper instantMapper = new OffsetInstantMapperImpl();

  /**
   * Converts a {@link DomainEventEntity} to an API-facing {@link DomainEvent} model.
   * Safely handles nulls: if the {@code entity} itself is null,
   * the method returns {@code null}.
   *
   * @param entity the {@link DomainEventEntity} to map (might be null)
   * @return a new {@link DomainEvent} object populated with mapped values 
   */
  default DomainEvent toDomainEvent(DomainEventEntity entity) {
    return DomainEvent.builder()
                      .applicationId(entity.getApplicationId())
                      .caseworkerId(entity.getCaseworkerId())
                      .domainEventType(entity.getType())
                      .eventDescription(entity.getData())
                      .createdAt(instantMapper.toOffsetDateTime(entity.getCreatedAt()))
                      .createdBy(entity.getCreatedBy())
                      .build();
  }
}

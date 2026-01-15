package uk.gov.justice.laa.dstew.access.service.impl;

import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.mapper.DomainEventMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEvent;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.service.EventHistoryService;
import uk.gov.justice.laa.dstew.access.specification.DomainEventSpecification;

public class EventHistoryServiceRdsImpl implements EventHistoryService {

  private final DomainEventRepository domainEventRepository;
  private final DomainEventMapper mapper;

  public EventHistoryServiceRdsImpl(DomainEventRepository domainEventRepository, DomainEventMapper mapper) {
    this.domainEventRepository = domainEventRepository;
    this.mapper = mapper;
  }

  /**
   * Provides a list of events associated with an application in createdAt ascending order.
   */
  @Override
  public List<ApplicationDomainEvent> getEvents(UUID applicationId,
                                                @Valid List<DomainEventType> eventType) {

    var filterEventType = DomainEventSpecification.filterEventTypes(eventType);
    Specification<DomainEventEntity> filter = DomainEventSpecification.filterApplicationId(applicationId)
        .and(filterEventType);
    Comparator<ApplicationDomainEvent> comparer = Comparator.comparing(ApplicationDomainEvent::getCreatedAt);
    return domainEventRepository.findAll(filter).stream().map(mapper::toDomainEvent).sorted(comparer).toList();
  }

}

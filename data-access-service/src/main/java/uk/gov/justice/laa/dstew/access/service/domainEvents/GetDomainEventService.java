package uk.gov.justice.laa.dstew.access.service.domainEvents;

import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.mapper.DomainEventMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEventResponse;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.specification.DomainEventSpecification;

/** Service to Get Domain events. */
@RequiredArgsConstructor
@Service
public class GetDomainEventService {
  private final DomainEventRepository domainEventRepository;
  private final DomainEventMapper mapper;

  /** Provides a list of events associated with an application in createdAt ascending order. */
  @AllowApiCaseworker
  public List<ApplicationDomainEventResponse> getEvents(
      UUID applicationId, @Valid List<DomainEventType> eventType) {

    var filterEventType = DomainEventSpecification.filterEventTypes(eventType);
    Specification<DomainEventEntity> filter =
        DomainEventSpecification.filterApplicationId(applicationId).and(filterEventType);

    Comparator<ApplicationDomainEventResponse> comparer =
        Comparator.comparing(ApplicationDomainEventResponse::getCreatedAt);
    return domainEventRepository.findAll(filter).stream()
        .map(mapper::toDomainEvent)
        .sorted(comparer)
        .toList();
  }
}

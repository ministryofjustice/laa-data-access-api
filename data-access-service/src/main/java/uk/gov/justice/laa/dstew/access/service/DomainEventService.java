package uk.gov.justice.laa.dstew.access.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.exception.DomainEventPublishException;
import uk.gov.justice.laa.dstew.access.mapper.DomainEventMapper;
import uk.gov.justice.laa.dstew.access.model.AssignApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.DomainEvent;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;

/**
 * Service class for managing domain events.
 */
@Service
@RequiredArgsConstructor
public class DomainEventService {
  private final DomainEventRepository domainEventRepository;
  private final ObjectMapper objectMapper;
  private final DomainEventMapper mapper;

  /**
   * posts a domain event {@link DomainEventEntity} object.
   *
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void saveAssignApplicationDomainEvent(
                    UUID applicationId,
                    UUID caseworkerId,
                    String eventDescription) {

    AssignApplicationDomainEventDetails data = AssignApplicationDomainEventDetails.builder()
            .applicationId(applicationId)
            .caseWorkerId(caseworkerId)
            .createdAt(Instant.now())
            .createdBy("")
            .eventDescription(eventDescription)
            .build();

    DomainEventEntity domainEventEntity = null;
    try {
      domainEventEntity = DomainEventEntity.builder()
                  .applicationId(applicationId)
                  .caseworkerId(caseworkerId)
                  .createdAt(Instant.now())
                  .createdBy("")
                  .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
                  .data(objectMapper.writeValueAsString(data))
                  .build();
    } catch (JsonProcessingException e) {
      throw new DomainEventPublishException(String.format("Unable to save Domain Event of type: %s",
                  DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER.name()));
    }
    domainEventRepository.save(domainEventEntity);
  }

  /**
  * Provides a list of events associated with an application in createdAt ascending order.
  */
  public List<DomainEvent> getEvents(Specification<DomainEventEntity> filter) {
    Comparator<DomainEvent> comparer = Comparator.comparing(DomainEvent::getCreatedAt);
    return repository.findAll(filter).stream().map(mapper::toDomainEvent).sorted(comparer).toList();
  }
}
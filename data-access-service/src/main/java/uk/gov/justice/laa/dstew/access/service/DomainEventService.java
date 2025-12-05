package uk.gov.justice.laa.dstew.access.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.model.DomainEventData;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;

/**
 * Service class for managing domain events.
 */
@Service
public class DomainEventService {
  private final DomainEventRepository domainEventRepository;

  /**
   * Constructs a new {@link DomainEventService} with the required repository and mapper.
   *
   * @param domainEventRepository the repository used to access domain event data.
   */
  public DomainEventService(DomainEventRepository domainEventRepository) {
    this.domainEventRepository = domainEventRepository;
  }

  /**
   * posts a domain event {@link DomainEventEntity} object.
   *
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void postEvent(UUID applicationId,
                        UUID caseWorkerId,
                        DomainEventType event,
                        Instant createdAt,
                        String createdBy
                        ) {
    DomainEventData data = DomainEventData.builder()
        .applicationId(applicationId)
        .caseWorkerId(caseWorkerId)
        .createdBy(createdBy)
        .createdBy(createdBy)
        .eventDescription(event.name())
        .build();

    DomainEventEntity domainEventEntity = DomainEventEntity.builder()
        .applicationId(applicationId)
        .caseWorkerId(caseWorkerId)
        .type(event)
        .createdAt(createdAt)
        .createdBy(createdBy)
        .data(null)
        .build();
    domainEventRepository.save(domainEventEntity);
  }
}
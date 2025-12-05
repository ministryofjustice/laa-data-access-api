package uk.gov.justice.laa.dstew.access.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
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
  public void postEvent(DomainEventEntity domainEventEntity) {
    domainEventRepository.save(domainEventEntity);
  }
}

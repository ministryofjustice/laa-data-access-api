package uk.gov.justice.laa.dstew.access.infrastructure.jpa.createapplication;

import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.service.DomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.infrastructure.DomainEventGateway;

/**
 * JPA/service implementation of DomainEventGateway. Wired via CreateApplicationConfig
 * (no @Component).
 */
public class DomainEventJpaGateway implements DomainEventGateway {

  private final DomainEventService domainEventService;

  public DomainEventJpaGateway(DomainEventService domainEventService) {
    this.domainEventService = domainEventService;
  }

  @Override
  public void saveCreatedEvent(ApplicationDomain saved) {
    domainEventService.saveCreateApplicationDomainEvent(saved, null);
  }
}

package uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.service.DomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.DomainEventGateway;

/** JPA/service implementation of DomainEventGateway. Wired via config (no @Component). */
public class DomainEventJpaGateway implements DomainEventGateway {

  private final DomainEventService domainEventService;

  public DomainEventJpaGateway(DomainEventService domainEventService) {
    this.domainEventService = domainEventService;
  }

  @Override
  public void saveCreatedEvent(ApplicationDomain saved, String serialisedRequest) {
    domainEventService.saveCreateApplicationDomainEvent(saved, serialisedRequest, null);
  }

  @Override
  public void saveMakeDecisionEvent(
      UUID applicationId,
      UUID caseworkerId,
      String serialisedRequest,
      String eventDescription,
      String domainEventTypeName) {
    DomainEventType type = DomainEventType.valueOf(domainEventTypeName);
    domainEventService.saveMakeDecisionDomainEvent(
        applicationId, caseworkerId, serialisedRequest, eventDescription, type);
  }
}

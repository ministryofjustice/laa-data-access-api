package uk.gov.justice.laa.dstew.access.usecase.createapplication.infrastructure;

import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;

/** Gateway interface for publishing domain events. */
public interface DomainEventGateway {
  void saveCreatedEvent(ApplicationDomain saved);
}

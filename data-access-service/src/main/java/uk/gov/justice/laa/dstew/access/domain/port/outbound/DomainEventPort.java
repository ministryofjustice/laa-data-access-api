package uk.gov.justice.laa.dstew.access.domain.port.outbound;

import uk.gov.justice.laa.dstew.access.domain.model.Application;
import uk.gov.justice.laa.dstew.access.domain.port.inbound.CreateApplicationCommand;

/** Driven port (outbound) for publishing domain events related to application creation. */
public interface DomainEventPort {

  /**
   * Publishes an APPLICATION_CREATED domain event.
   *
   * @param application the saved application
   * @param command the original creation command
   */
  void publishApplicationCreated(Application application, CreateApplicationCommand command);
}

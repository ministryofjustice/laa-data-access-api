package uk.gov.justice.laa.dstew.access.domain.port.inbound;

import java.util.UUID;

/** Driving port (inbound) for the create-application use case. */
public interface CreateApplicationUseCase {

  /**
   * Creates a new application from the given command.
   *
   * @param command pre-validated creation data
   * @return the UUID of the newly created application
   */
  UUID createApplication(CreateApplicationCommand command);
}

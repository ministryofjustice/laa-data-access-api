package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallnotesforapplication;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.application.ApplicationJpaGateway;
import uk.gov.justice.laa.dstew.access.usecase.getallnotesforapplication.infrastructure.GetAllNotesForApplicationApplicationGateway;

/**
 * JPA gateway for checking application existence for the get-all-notes-for-application use case.
 */
public class GetAllNotesForApplicationApplicationJpaGateway
    implements GetAllNotesForApplicationApplicationGateway {

  private final ApplicationJpaGateway applicationJpaGateway;

  /**
   * Constructs the gateway.
   *
   * @param applicationJpaGateway the JPA gateway for application operations
   */
  public GetAllNotesForApplicationApplicationJpaGateway(
      ApplicationJpaGateway applicationJpaGateway) {
    this.applicationJpaGateway = applicationJpaGateway;
  }

  @Override
  public boolean exists(UUID applicationId) {
    return applicationJpaGateway.applicationExists(applicationId);
  }
}

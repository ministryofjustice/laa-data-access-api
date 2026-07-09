package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallnotesforapplication;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.usecase.getallnotesforapplication.infrastructure.GetAllNotesForApplicationApplicationGateway;

/**
 * JPA gateway for checking application existence for the get-all-notes-for-application use case.
 */
public class GetAllNotesForApplicationApplicationJpaGateway
    implements GetAllNotesForApplicationApplicationGateway {

  private final ApplicationRepository applicationRepository;

  /**
   * Constructs the gateway.
   *
   * @param applicationRepository application repository
   */
  public GetAllNotesForApplicationApplicationJpaGateway(
      ApplicationRepository applicationRepository) {
    this.applicationRepository = applicationRepository;
  }

  @Override
  public boolean exists(UUID applicationId) {
    return applicationRepository.existsById(applicationId);
  }
}

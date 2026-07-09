package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getcertificate;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.usecase.getcertificate.infrastructure.GetCertificateApplicationGateway;

/** JPA implementation of {@link GetCertificateApplicationGateway}. */
public class GetCertificateApplicationJpaGateway implements GetCertificateApplicationGateway {

  private final ApplicationRepository applicationRepository;

  /**
   * Constructs the gateway.
   *
   * @param applicationRepository the Spring Data repository
   */
  public GetCertificateApplicationJpaGateway(ApplicationRepository applicationRepository) {
    this.applicationRepository = applicationRepository;
  }

  @Override
  public boolean applicationExists(UUID applicationId) {
    return applicationRepository.findById(applicationId).isPresent();
  }
}

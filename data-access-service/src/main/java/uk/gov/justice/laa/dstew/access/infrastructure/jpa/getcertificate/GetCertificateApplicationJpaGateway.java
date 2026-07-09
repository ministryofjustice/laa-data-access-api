package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getcertificate;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.usecase.getcertificate.infrastructure.GetCertificateApplicationGateway;

/**
 * JPA gateway that checks application existence for the get-certificate use case. No
 * {@code @Component} — wired in {@link
 * uk.gov.justice.laa.dstew.access.config.GetCertificateConfig}.
 */
public class GetCertificateApplicationJpaGateway implements GetCertificateApplicationGateway {

  private final ApplicationRepository applicationRepository;

  /**
   * Constructs the gateway.
   *
   * @param applicationRepository application repository
   */
  public GetCertificateApplicationJpaGateway(ApplicationRepository applicationRepository) {
    this.applicationRepository = applicationRepository;
  }

  @Override
  public boolean applicationExists(UUID applicationId) {
    return applicationRepository.findById(applicationId).isPresent();
  }
}

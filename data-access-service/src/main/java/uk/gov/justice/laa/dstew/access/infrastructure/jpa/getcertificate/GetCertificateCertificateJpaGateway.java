package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getcertificate;

import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.repository.CertificateRepository;
import uk.gov.justice.laa.dstew.access.usecase.getcertificate.infrastructure.GetCertificateCertificateGateway;

/**
 * JPA gateway that retrieves certificate data for the get-certificate use case. No
 * {@code @Component} — wired in {@link
 * uk.gov.justice.laa.dstew.access.config.GetCertificateConfig}.
 */
public class GetCertificateCertificateJpaGateway implements GetCertificateCertificateGateway {

  private final CertificateRepository certificateRepository;
  private final GetCertificateGatewayMapper gatewayMapper;

  /**
   * Constructs the gateway.
   *
   * @param certificateRepository certificate repository
   * @param gatewayMapper gateway mapper
   */
  public GetCertificateCertificateJpaGateway(
      CertificateRepository certificateRepository, GetCertificateGatewayMapper gatewayMapper) {
    this.certificateRepository = certificateRepository;
    this.gatewayMapper = gatewayMapper;
  }

  @Override
  public Optional<CertificateDomain> findCertificateDomainByApplicationId(UUID applicationId) {
    return certificateRepository
        .findByApplicationId(applicationId)
        .map(gatewayMapper::toCertificateDomain);
  }
}

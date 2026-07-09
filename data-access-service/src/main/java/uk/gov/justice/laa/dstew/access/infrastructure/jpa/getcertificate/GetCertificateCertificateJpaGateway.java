package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getcertificate;

import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.repository.CertificateRepository;
import uk.gov.justice.laa.dstew.access.usecase.getcertificate.infrastructure.GetCertificateCertificateGateway;

/** JPA implementation of {@link GetCertificateCertificateGateway}. */
public class GetCertificateCertificateJpaGateway implements GetCertificateCertificateGateway {

  private final CertificateRepository certificateRepository;
  private final GetCertificateGatewayMapper gatewayMapper;

  /**
   * Constructs the gateway.
   *
   * @param certificateRepository the Spring Data repository
   * @param gatewayMapper the gateway mapper
   */
  public GetCertificateCertificateJpaGateway(
      CertificateRepository certificateRepository, GetCertificateGatewayMapper gatewayMapper) {
    this.certificateRepository = certificateRepository;
    this.gatewayMapper = gatewayMapper;
  }

  @Override
  public Optional<CertificateDomain> findByApplicationId(UUID applicationId) {
    return certificateRepository
        .findByApplicationId(applicationId)
        .map(gatewayMapper::toCertificateDomain);
  }
}

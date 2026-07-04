package uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision;

import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;
import uk.gov.justice.laa.dstew.access.repository.CertificateRepository;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.MakeDecisionCertificateGateway;

/** JPA implementation of {@link MakeDecisionCertificateGateway}. */
public class MakeDecisionCertificateJpaGateway implements MakeDecisionCertificateGateway {

  private final CertificateRepository certificateRepository;
  private final MakeDecisionGatewayMapper mapper;

  /**
   * Constructs the gateway.
   *
   * @param certificateRepository the Spring Data repository
   * @param mapper the gateway mapper
   */
  public MakeDecisionCertificateJpaGateway(
      CertificateRepository certificateRepository, MakeDecisionGatewayMapper mapper) {
    this.certificateRepository = certificateRepository;
    this.mapper = mapper;
  }

  @Override
  public Optional<CertificateDomain> findByApplicationId(UUID applicationId) {
    return certificateRepository
        .findByApplicationId(applicationId)
        .map(mapper::toCertificateDomain);
  }

  /**
   * Saves a certificate. For UPDATE (non-null id), reloads the managed entity to preserve
   * Hibernate-managed audit timestamps ({@literal @}CreationTimestamp). For INSERT (null id),
   * creates a new entity via the mapper.
   */
  @Override
  public void save(CertificateDomain domain) {
    CertificateEntity entity;
    if (domain.id() != null) {
      entity =
          certificateRepository
              .findById(domain.id())
              .orElseGet(() -> mapper.toCertificateEntity(domain));
      entity.setCertificateContent(domain.certificateContent());
    } else {
      entity = mapper.toCertificateEntity(domain);
    }
    certificateRepository.save(entity);
  }

  @Override
  public void deleteByApplicationId(UUID applicationId) {
    certificateRepository.deleteByApplicationId(applicationId);
  }
}

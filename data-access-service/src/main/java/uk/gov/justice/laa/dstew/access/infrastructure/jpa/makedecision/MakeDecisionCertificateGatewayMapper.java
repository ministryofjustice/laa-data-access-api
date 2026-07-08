package uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision;

import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;

/**
 * Converts domain records ↔ JPA entities for certificate operations in the makeDecision use case.
 */
public class MakeDecisionCertificateGatewayMapper {

  /**
   * Maps a {@link CertificateEntity} to a {@link CertificateDomain}.
   *
   * @param certificateEntity the JPA certificate entity
   * @return the domain
   */
  public CertificateDomain toCertificateDomain(CertificateEntity certificateEntity) {
    return CertificateDomain.builder()
        .id(certificateEntity.getId())
        .applicationId(certificateEntity.getApplicationId())
        .certificateContent(certificateEntity.getCertificateContent())
        .createdBy(certificateEntity.getCreatedBy())
        .updatedBy(certificateEntity.getUpdatedBy())
        .build();
  }

  /**
   * Maps a {@link CertificateDomain} to a new {@link CertificateEntity}. Use only for INSERT (id is
   * null). For UPDATE, use the load-and-apply pattern.
   *
   * @param certificateDomain the domain
   * @return a new entity for persistence
   */
  public CertificateEntity toCertificateEntity(CertificateDomain certificateDomain) {
    CertificateEntity entity = new CertificateEntity();
    entity.setApplicationId(certificateDomain.applicationId());
    entity.setCertificateContent(certificateDomain.certificateContent());
    entity.setCreatedBy(certificateDomain.createdBy());
    entity.setUpdatedBy(certificateDomain.updatedBy());
    return entity;
  }
}

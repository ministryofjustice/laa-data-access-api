package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getcertificate;

import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;

/** Maps JPA entities to domain records for the getCertificate use case. */
public class GetCertificateGatewayMapper {

  /**
   * Maps a {@link CertificateEntity} to a {@link CertificateDomain}.
   *
   * @param certificateEntity the JPA certificate entity
   * @return the domain record
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
}

package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getcertificate;

import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;

/** Mapper converting {@link CertificateEntity} to {@link CertificateDomain}. */
public class GetCertificateGatewayMapper {

  /**
   * Converts a certificate entity to a certificate domain record.
   *
   * @param certificateEntity the certificate entity
   * @return the certificate domain record
   */
  public CertificateDomain toCertificateDomain(CertificateEntity certificateEntity) {
    return new CertificateDomain(certificateEntity.getCertificateContent());
  }
}

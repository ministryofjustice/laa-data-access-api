package uk.gov.justice.laa.dstew.access.usecase.getcertificate.infrastructure;

import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;

/** Gateway for retrieving a certificate in the getCertificate use case. */
public interface GetCertificateCertificateGateway {

  /**
   * Finds a certificate by application id.
   *
   * @param applicationId application id
   * @return optional domain record
   */
  Optional<CertificateDomain> findByApplicationId(UUID applicationId);
}

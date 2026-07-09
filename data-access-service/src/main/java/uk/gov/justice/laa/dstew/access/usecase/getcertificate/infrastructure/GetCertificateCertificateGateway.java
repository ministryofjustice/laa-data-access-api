package uk.gov.justice.laa.dstew.access.usecase.getcertificate.infrastructure;

import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;

/** Gateway for retrieving certificate domain data in the get-certificate use case. */
public interface GetCertificateCertificateGateway {

  /**
   * Finds the certificate for the given application id.
   *
   * @param applicationId application id
   * @return optional certificate domain
   */
  Optional<CertificateDomain> findCertificateDomainByApplicationId(UUID applicationId);
}

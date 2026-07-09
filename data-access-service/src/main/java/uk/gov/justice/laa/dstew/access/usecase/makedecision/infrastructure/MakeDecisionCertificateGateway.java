package uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure;

import java.util.Optional;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;

/** Gateway interface for certificate persistence in the makeDecision use case. */
public interface MakeDecisionCertificateGateway {

  /**
   * Returns the certificate domain for the given application ID, or empty if not found.
   *
   * @param applicationId the application UUID
   * @return an Optional containing the domain, or empty
   */
  Optional<CertificateDomain> findByApplicationId(UUID applicationId);

  /**
   * Saves a certificate (INSERT when domain.id() is null, UPDATE otherwise).
   *
   * @param domain the certificate domain to save
   */
  void save(CertificateDomain domain);

  /**
   * Deletes the certificate for the given application, if one exists.
   *
   * @param applicationId the application UUID
   */
  void deleteByApplicationId(UUID applicationId);
}

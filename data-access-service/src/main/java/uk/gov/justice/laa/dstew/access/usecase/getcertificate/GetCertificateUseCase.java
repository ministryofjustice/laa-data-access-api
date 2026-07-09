package uk.gov.justice.laa.dstew.access.usecase.getcertificate;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.usecase.getcertificate.infrastructure.GetCertificateApplicationGateway;
import uk.gov.justice.laa.dstew.access.usecase.getcertificate.infrastructure.GetCertificateCertificateGateway;

/** Orchestrates retrieval of the certificate for a given application. */
public class GetCertificateUseCase {

  private final GetCertificateApplicationGateway applicationGateway;
  private final GetCertificateCertificateGateway certificateGateway;

  /**
   * Constructs the use case.
   *
   * @param applicationGateway gateway for application existence checks
   * @param certificateGateway gateway for certificate retrieval
   */
  public GetCertificateUseCase(
      GetCertificateApplicationGateway applicationGateway,
      GetCertificateCertificateGateway certificateGateway) {
    this.applicationGateway = applicationGateway;
    this.certificateGateway = certificateGateway;
  }

  /**
   * Returns the certificate domain for the given application id.
   *
   * @param applicationId application id
   * @return certificate domain
   * @throws ResourceNotFoundException if no application or no certificate is found
   */
  @AllowApiCaseworker
  public CertificateDomain execute(UUID applicationId) {
    if (!applicationGateway.applicationExists(applicationId)) {
      throw new ResourceNotFoundException(
          String.format("No application found with id: %s", applicationId));
    }

    return certificateGateway
        .findCertificateDomainByApplicationId(applicationId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    String.format("No certificate found for application id: %s", applicationId)));
  }
}

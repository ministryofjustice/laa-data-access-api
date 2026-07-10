package uk.gov.justice.laa.dstew.access.usecase.getcertificate;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.usecase.getcertificate.infrastructure.GetCertificateCertificateGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;

/** Orchestrates retrieval of a certificate for a given application. */
@RequiredArgsConstructor
public class GetCertificateUseCase {

  private final ApplicationGateway applicationGateway;
  private final GetCertificateCertificateGateway certificateGateway;

  /**
   * Returns the certificate domain for the supplied application.
   *
   * @param applicationId application id
   * @return certificate domain
   * @throws ResourceNotFoundException if the application or certificate does not exist
   */
  @AllowApiCaseworker
  public CertificateDomain execute(UUID applicationId) {
    if (!applicationGateway.applicationExists(applicationId)) {
      throw new ResourceNotFoundException(
          String.format("No application found with id: %s", applicationId));
    }

    return certificateGateway
        .findByApplicationId(applicationId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    String.format("No certificate found for application id: %s", applicationId)));
  }
}

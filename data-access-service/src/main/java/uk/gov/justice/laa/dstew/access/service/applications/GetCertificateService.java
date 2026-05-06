package uk.gov.justice.laa.dstew.access.service.applications;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CertificateRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Service for retrieving certificate data. */
@Service
@RequiredArgsConstructor
public class GetCertificateService {

  private final CertificateRepository certificateRepository;
  private final ApplicationRepository applicationRepository;

  /** Retrieves the certificate content for a given application. */
  @AllowApiCaseworker
  public Map<String, Object> getCertificate(final UUID applicationId) {
    applicationRepository
        .findById(applicationId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    String.format("No application found with id: %s", applicationId)));

    return certificateRepository
        .findByApplicationId(applicationId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    String.format("No certificate found for application id: %s", applicationId)))
        .getCertificateContent();
  }
}

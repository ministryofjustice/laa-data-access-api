package uk.gov.justice.laa.dstew.access.service.common;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;

/** Shared logic for services. */
@RequiredArgsConstructor
@Component
public class ServiceUtilities {

  private final ApplicationRepository applicationRepository;
  private final CaseworkerRepository caseworkerRepository;

  /**
   * Check existence of an application by ID.
   *
   * @param id UUID of application
   * @return found entity
   */
  public ApplicationEntity checkIfApplicationExists(final UUID id) {
    return applicationRepository
        .findById(id)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    String.format("No application found with id: %s", id)));
  }

  /**
   * Check existence of a caseworker by ID.
   *
   * @param caseworkerId userid of caseworker
   * @return found entity
   */
  public CaseworkerEntity checkIfCaseworkerExists(final UUID caseworkerId) {
    return caseworkerRepository
        .findById(caseworkerId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    String.format("No caseworker found with id: %s", caseworkerId)));
  }

  /**
   * Gets the caseworker id from an application.
   *
   * @param applicationId UUID application id
   * @param application application entity
   * @return caseworker id
   */
  public static UUID getCaseworkerId(UUID applicationId, ApplicationEntity application) {
    final CaseworkerEntity caseworker = application.getCaseworker();
    // This logic will be implemented in the next iteration when security is implemented in the
    // service
    //    if (caseworker == null) {
    //      throw new ResourceNotFoundException(
    //          String.format("Caseworker not found for application id: %s", applicationId)
    //      );
    //    }
    return caseworker != null ? caseworker.getId() : null;
  }

  /**
   * Check if an application has a caseworker assigned already and checks if the assigned caseworker
   * matches the given caseworker.
   */
  public static boolean applicationCurrentCaseworkerIsCaseworker(
      ApplicationEntity application, CaseworkerEntity caseworker) {
    return application.getCaseworker() != null && application.getCaseworker().equals(caseworker);
  }
}

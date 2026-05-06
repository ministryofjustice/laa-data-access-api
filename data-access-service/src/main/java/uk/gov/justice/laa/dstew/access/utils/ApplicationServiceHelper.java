package uk.gov.justice.laa.dstew.access.utils;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;

/** Shared logic for checking an application exists. */
public class ApplicationServiceHelper {

  /**
   * Check existence of an application by ID.
   *
   * @param id UUID of application
   * @param applicationRepository application repository
   * @return found entity
   * @throws ResourceNotFoundException if no application found with the given ID.
   */
  public static ApplicationEntity getExistingApplication(
      final UUID id, ApplicationRepository applicationRepository) {
    return applicationRepository
        .findById(id)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    String.format("No application found with id: %s", id)));
  }
}

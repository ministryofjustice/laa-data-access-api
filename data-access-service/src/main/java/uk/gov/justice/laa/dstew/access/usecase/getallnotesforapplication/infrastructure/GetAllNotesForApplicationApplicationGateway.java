package uk.gov.justice.laa.dstew.access.usecase.getallnotesforapplication.infrastructure;

import java.util.UUID;

/** Gateway for checking application existence for the get-all-notes-for-application use case. */
public interface GetAllNotesForApplicationApplicationGateway {

  /**
   * Checks whether an application with the given id exists.
   *
   * @param applicationId application id
   * @return {@code true} if the application exists, {@code false} otherwise
   */
  boolean exists(UUID applicationId);
}

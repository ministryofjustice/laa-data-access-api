package uk.gov.justice.laa.dstew.access.exception;

import java.util.UUID;

/** Thrown when an application ID is reused with a different creation payload or schema version. */
public class ApplicationCreationConflictException extends RuntimeException {

  private final UUID applicationId;

  public ApplicationCreationConflictException(UUID applicationId) {
    super("Application already exists with a different payload for ID: " + applicationId);
    this.applicationId = applicationId;
  }

  public UUID getApplicationId() {
    return applicationId;
  }
}

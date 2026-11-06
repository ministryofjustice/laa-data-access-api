package uk.gov.justice.laa.dstew.access.exception;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.shared.logging.ClientFacingException;

/** Thrown when an application ID is reused with a different creation payload or schema version. */
@ExcludeFromGeneratedCodeCoverage
public class ApplicationCreationConflictException extends RuntimeException
    implements ClientFacingException {

  private final UUID applicationId;

  public ApplicationCreationConflictException(UUID applicationId) {
    super("Application already exists with a different payload for ID: " + applicationId);
    this.applicationId = applicationId;
  }

  public UUID getApplicationId() {
    return applicationId;
  }
}

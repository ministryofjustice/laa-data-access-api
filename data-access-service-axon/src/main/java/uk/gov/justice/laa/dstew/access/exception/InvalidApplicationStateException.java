package uk.gov.justice.laa.dstew.access.exception;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.shared.logging.ClientFacingException;

/** Raised when an operation is not valid for the Application's lifecycle state. */
@ExcludeFromGeneratedCodeCoverage
public class InvalidApplicationStateException extends RuntimeException
    implements ClientFacingException {

  public InvalidApplicationStateException(UUID applicationId, String status) {
    super("Application " + applicationId + " cannot be made ready from status " + status);
  }
}

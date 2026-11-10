package uk.gov.justice.laa.dstew.access.exception;

import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.shared.logging.ClientFacingException;

/** Raised when a command references an aggregate that does not exist. */
@ExcludeFromGeneratedCodeCoverage
public class ResourceNotFoundException extends RuntimeException implements ClientFacingException {

  public ResourceNotFoundException(String message) {
    super(message);
  }
}

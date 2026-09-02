package uk.gov.justice.laa.dstew.access.exception;

import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Raised when a command references an aggregate that does not exist. */
@ExcludeFromGeneratedCodeCoverage
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String message) {
    super(message);
  }
}

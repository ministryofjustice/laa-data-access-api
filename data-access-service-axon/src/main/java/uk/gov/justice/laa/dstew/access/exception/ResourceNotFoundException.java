package uk.gov.justice.laa.dstew.access.exception;

/** Raised when a command references an aggregate that does not exist. */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String message) {
    super(message);
  }
}

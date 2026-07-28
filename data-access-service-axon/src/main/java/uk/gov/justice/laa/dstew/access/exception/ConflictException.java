package uk.gov.justice.laa.dstew.access.exception;

/** Raised when a command is rejected because the target is in an incompatible state. */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}

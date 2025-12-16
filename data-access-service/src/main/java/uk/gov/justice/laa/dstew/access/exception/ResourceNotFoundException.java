package uk.gov.justice.laa.dstew.access.exception;

/**
 * The exception thrown when application not found.
 */
public class ResourceNotFoundException extends RuntimeException {

  /**
   * Constructor for ApplicationNotFoundException.
   *
   * @param message the error message
   */
  public ResourceNotFoundException(String message) {
    super(message);
  }
}

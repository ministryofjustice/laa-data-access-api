package uk.gov.justice.laa.dstew.access.exception;

/**
 * The exception thrown when application not found.
 */
public class CaseworkerNotFoundException extends RuntimeException {

  /**
   * Constructor for CaseworkerNotFoundException.
   *
   * @param message the error message
   */
  public CaseworkerNotFoundException(String message) {
    super(message);
  }
}

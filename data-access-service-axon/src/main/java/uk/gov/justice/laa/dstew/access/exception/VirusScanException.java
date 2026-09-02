package uk.gov.justice.laa.dstew.access.exception;

/** The exception thrown when SDS virus scan gives a non-standard result. */
public class VirusScanException extends RuntimeException {
  public VirusScanException(String message) {
    super(message);
  }
}

package uk.gov.justice.laa.dstew.access.exception;

/** The exception thrown when a virus is detected in an uploaded file by SDS. */
public class VirusDetectedException extends RuntimeException {
  public VirusDetectedException(String message) {
    super(message);
  }
}

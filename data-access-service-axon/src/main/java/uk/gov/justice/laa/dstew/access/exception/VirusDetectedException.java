package uk.gov.justice.laa.dstew.access.exception;

import uk.gov.justice.laa.dstew.access.shared.logging.ClientFacingException;

/** The exception thrown when a virus is detected in an uploaded file by SDS. */
public class VirusDetectedException extends RuntimeException implements ClientFacingException {
  public VirusDetectedException(String message) {
    super(message);
  }
}

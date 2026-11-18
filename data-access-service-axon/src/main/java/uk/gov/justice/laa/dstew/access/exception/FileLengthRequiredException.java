package uk.gov.justice.laa.dstew.access.exception;

import uk.gov.justice.laa.dstew.access.shared.logging.ClientFacingException;

/** The exception thrown when file content length is not present in the SDS request. */
public class FileLengthRequiredException extends RuntimeException implements ClientFacingException {
  public FileLengthRequiredException(String message) {
    super(message);
  }
}

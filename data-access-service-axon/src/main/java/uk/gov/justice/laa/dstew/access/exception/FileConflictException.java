package uk.gov.justice.laa.dstew.access.exception;

import uk.gov.justice.laa.dstew.access.shared.logging.ClientFacingException;

/** The exception thrown when there is a file conflict in SDS. */
public class FileConflictException extends RuntimeException implements ClientFacingException {
  public FileConflictException(String message) {
    super(message);
  }
}

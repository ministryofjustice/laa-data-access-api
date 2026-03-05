package uk.gov.justice.laa.dstew.access.exception;

/**
 * The exception thrown when there is a file conflict in SDS.
 */
public class FileConflictException extends RuntimeException {
  public FileConflictException(String message) {
    super(message);
  }
}
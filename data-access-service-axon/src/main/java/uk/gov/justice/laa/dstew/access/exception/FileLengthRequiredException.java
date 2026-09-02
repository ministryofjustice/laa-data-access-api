package uk.gov.justice.laa.dstew.access.exception;

/** The exception thrown when file content length is not present in the SDS request. */
public class FileLengthRequiredException extends RuntimeException {
  public FileLengthRequiredException(String message) {
    super(message);
  }
}

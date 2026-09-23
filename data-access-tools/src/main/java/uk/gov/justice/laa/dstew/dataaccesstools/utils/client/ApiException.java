package uk.gov.justice.laa.dstew.dataaccesstools.utils.client;

public class ApiException extends RuntimeException {
  public ApiException(String message) {
    super(message);
  }

  public ApiException(String message, Throwable cause) {
    super(message, cause);
  }
}

package uk.gov.justice.laa.dstew.access.exception;

/** The exception thrown when the SDS OAuth2 token provider fails to obtain an access token. */
public class TokenProviderException extends RuntimeException {
  public TokenProviderException(String message) {
    super(message);
  }
}

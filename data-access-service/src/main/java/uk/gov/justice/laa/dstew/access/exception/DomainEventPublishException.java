package uk.gov.justice.laa.dstew.access.exception;

/**
 * The exception thrown when a domain event cannot be published.
 */
public class DomainEventPublishException extends RuntimeException {

  /**
   * Constructor for DomainEventPublishException.
   *
   * @param message the error message
   */
  public DomainEventPublishException(String message) {
    super(message);
  }
}
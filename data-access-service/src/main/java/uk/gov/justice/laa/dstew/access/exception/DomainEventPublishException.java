package uk.gov.justice.laa.dstew.access.exception;

import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** The exception thrown when a domain event cannot be published. */
@ExcludeFromGeneratedCodeCoverage
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

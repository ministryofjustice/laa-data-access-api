package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.shared.logging.ClientFacingException;

/** Raised when an application cannot be linked because it already belongs to another group. */
@ExcludeFromGeneratedCodeCoverage
public class ApplicationLinkConflictException extends RuntimeException
    implements ClientFacingException {

  /** Creates a conflict with a stable public message. */
  public ApplicationLinkConflictException(String message) {
    super(message);
  }
}

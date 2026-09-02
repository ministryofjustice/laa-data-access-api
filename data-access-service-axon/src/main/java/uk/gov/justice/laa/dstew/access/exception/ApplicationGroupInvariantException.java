package uk.gov.justice.laa.dstew.access.exception;

import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Raised when a command would violate an application-group invariant.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>An application specifying itself as its own lead (self-referential group).
 *   <li>An application that is already an associated member of a group attempting to become a lead.
 * </ul>
 */
@ExcludeFromGeneratedCodeCoverage
public class ApplicationGroupInvariantException extends RuntimeException {

  public ApplicationGroupInvariantException(String message) {
    super(message);
  }
}

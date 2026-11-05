package uk.gov.justice.laa.dstew.access.exception;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.shared.logging.ClientFacingException;

/** Raised when a command was based on a stale Prior Authority revision. */
@ExcludeFromGeneratedCodeCoverage
public class PriorAuthorityVersionConflictException extends RuntimeException
    implements ClientFacingException {

  /** Creates a conflict message describing the stale submission/version pair. */
  public PriorAuthorityVersionConflictException(UUID submissionId, long version) {
    super(
        "Prior authority with submission id "
            + submissionId
            + " and version "
            + version
            + " not found");
  }
}

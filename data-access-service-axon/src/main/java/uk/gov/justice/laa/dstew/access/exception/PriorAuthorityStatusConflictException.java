package uk.gov.justice.laa.dstew.access.exception;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Raised when a decision is attempted for a PriorAuthority that is no longer pending. */
@ExcludeFromGeneratedCodeCoverage
public class PriorAuthorityStatusConflictException extends RuntimeException {

  public PriorAuthorityStatusConflictException(UUID submissionId, String status) {
    super("Prior authority " + submissionId + " cannot be decided from status " + status);
  }
}

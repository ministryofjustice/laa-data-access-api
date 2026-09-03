package uk.gov.justice.laa.dstew.access.exception;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Thrown when a submit or draft-save command targets a Prior Authority with no in-progress draft.
 */
@ExcludeFromGeneratedCodeCoverage
public class PriorAuthorityNotInProgressException extends RuntimeException {

  private final UUID submissionId;

  /** Creates an exception for a submission ID that is not in a draftable or submittable state. */
  public PriorAuthorityNotInProgressException(UUID submissionId) {
    super("Prior Authority " + submissionId + " has no in-progress draft");
    this.submissionId = submissionId;
  }

  public UUID getSubmissionId() {
    return submissionId;
  }
}

package uk.gov.justice.laa.dstew.access.exception;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Thrown when a prior-authority submission ID is reused with a different creation payload. */
@ExcludeFromGeneratedCodeCoverage
public class PriorAuthorityCreationConflictException extends RuntimeException {

  private final UUID submissionId;

  /**
   * Creates a conflict exception for a submission ID that already exists with different content.
   */
  public PriorAuthorityCreationConflictException(UUID submissionId) {
    super(
        "Prior authority already exists with a different payload for submission: " + submissionId);
    this.submissionId = submissionId;
  }

  public UUID getSubmissionId() {
    return submissionId;
  }
}

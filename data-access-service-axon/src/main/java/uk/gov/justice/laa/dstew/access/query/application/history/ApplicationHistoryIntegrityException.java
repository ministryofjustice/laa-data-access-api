package uk.gov.justice.laa.dstew.access.query.application.history;

import java.util.UUID;
import lombok.Getter;

/**
 * Thrown when stored application-history data violates a required invariant, such as conflicting
 * prior-authority types for the same submission.
 */
@Getter
public class ApplicationHistoryIntegrityException extends RuntimeException {

  /** The application whose history contains inconsistent data. */
  private final UUID applicationId;

  /** The submission whose rows are inconsistent, when the failure is PA-scoped. */
  private final UUID submissionId;

  /** Server-safe diagnostic message; never surfaced in the HTTP response. */
  private final String reason;

  /**
   * Constructs an ApplicationHistoryIntegrityException for the provided identifiers and reason.
   *
   * @param applicationId the application whose history is inconsistent
   * @param submissionId the submission whose rows are inconsistent
   * @param reason server-side diagnostic message
   */
  public ApplicationHistoryIntegrityException(
      UUID applicationId, UUID submissionId, String reason) {
    super(
        "Application history integrity failure"
            + " [applicationId="
            + applicationId
            + ", submissionId="
            + submissionId
            + "]: "
            + reason);
    this.applicationId = applicationId;
    this.submissionId = submissionId;
    this.reason = reason;
  }
}

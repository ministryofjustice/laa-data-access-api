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
  private final UUID priorAuthorityId;

  /** Server-safe diagnostic message; never surfaced in the HTTP response. */
  private final String reason;

  /**
   * Constructs an ApplicationHistoryIntegrityException for the provided identifiers and reason.
   *
   * @param applicationId the application whose history is inconsistent
   * @param priorAuthorityId the submission whose rows are inconsistent
   * @param reason server-side diagnostic message
   */
  public ApplicationHistoryIntegrityException(
      UUID applicationId, UUID priorAuthorityId, String reason) {
    super(
        "Application history integrity failure [applicationId=%s, priorAuthorityId=%s]: %s"
            .formatted(applicationId, priorAuthorityId, reason));
    this.applicationId = applicationId;
    this.priorAuthorityId = priorAuthorityId;
    this.reason = reason;
  }
}

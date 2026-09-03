package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import java.time.Instant;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;

/** Sensitive data associated with one prior-authority submission. */
public record PriorAuthorityDataPayload(
    UUID submissionId,
    UUID applicationId,
    PriorAuthorityContent content,
    String serialisedRequest,
    Instant submittedAt,
    String assignmentDescription) {
  public PriorAuthorityDataPayload(
      UUID submissionId,
      UUID applicationId,
      PriorAuthorityContent content,
      String serialisedRequest,
      Instant submittedAt) {
    this(submissionId, applicationId, content, serialisedRequest, submittedAt, null);
  }
}

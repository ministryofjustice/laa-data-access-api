package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/**
 * The parts of an open Prior Authority draft that use cases need before dispatching a command.
 *
 * @param applicationId the parent application, or {@code null} when there is no open draft
 * @param originalFilename the retained filename of the requested document, if any
 * @param submitted whether the Prior Authority exists but has already been submitted
 */
public record PriorAuthorityDraftSummary(
    UUID applicationId, String originalFilename, boolean submitted) {

  /** The answer when there is no Prior Authority, or its draft content has been erased. */
  public static final PriorAuthorityDraftSummary NONE =
      new PriorAuthorityDraftSummary(null, null, false);

  /** The answer when the Prior Authority has been submitted, so it has no open draft. */
  public static final PriorAuthorityDraftSummary SUBMITTED =
      new PriorAuthorityDraftSummary(null, null, true);

  /** Creates the summary of an open draft. */
  public PriorAuthorityDraftSummary(UUID applicationId, String originalFilename) {
    this(applicationId, originalFilename, false);
  }

  /** Returns whether an open draft was found. */
  public boolean exists() {
    return applicationId != null;
  }

  /**
   * Rejects document changes once submitted, as the document command handlers do, so the use case
   * fails the same way before touching SDS.
   */
  public void requireDocumentsChangeable() {
    if (submitted) {
      throw new ValidationException(
          List.of("Documents can only be changed on a prior authority draft"));
    }
  }
}

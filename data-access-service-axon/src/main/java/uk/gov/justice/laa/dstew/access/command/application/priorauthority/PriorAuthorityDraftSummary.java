package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.util.UUID;

/**
 * The parts of an open Prior Authority draft that use cases need before dispatching a command.
 *
 * @param applicationId the parent application, or {@code null} when there is no open draft
 * @param originalFilename the retained filename of the requested document, if any
 */
public record PriorAuthorityDraftSummary(UUID applicationId, String originalFilename) {

  /** The answer when there is no open draft. */
  public static final PriorAuthorityDraftSummary NONE = new PriorAuthorityDraftSummary(null, null);

  /** Returns whether an open draft was found. */
  public boolean exists() {
    return applicationId != null;
  }
}

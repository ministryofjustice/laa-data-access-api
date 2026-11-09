package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.util.Map;
import java.util.UUID;

/**
 * The parts of an open Prior Authority draft that use cases need before dispatching a command.
 *
 * @param applicationId the parent application, or {@code null} when there is no open draft
 * @param documentFilenames the retained original filename of each live document
 */
public record PriorAuthorityDraftSummary(UUID applicationId, Map<UUID, String> documentFilenames) {

  /** The answer when there is no open draft. */
  public static final PriorAuthorityDraftSummary NONE =
      new PriorAuthorityDraftSummary(null, Map.of());

  /** Normalises the filenames to an immutable copy. */
  public PriorAuthorityDraftSummary {
    documentFilenames = documentFilenames == null ? Map.of() : Map.copyOf(documentFilenames);
  }

  /** Returns whether an open draft was found. */
  public boolean exists() {
    return applicationId != null;
  }
}

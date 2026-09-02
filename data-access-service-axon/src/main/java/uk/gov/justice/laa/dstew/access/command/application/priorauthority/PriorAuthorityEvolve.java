package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Event-fold functions for {@link PriorAuthorityState}. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PriorAuthorityEvolve {

  /** Applies a {@link PriorAuthorityCreatedEvent} to the given state. */
  public static void apply(PriorAuthorityState state, PriorAuthorityCreatedEvent event) {
    state.submissionId = event.submissionId();
    state.applicationId = event.applicationId();
    state.dataVersion = event.dataVersion();
    state.requestFingerprint = event.requestFingerprint();
    state.status = event.status();
    state.schemaVersion = event.schemaVersion();
  }

  /** Applies a {@link PriorAuthorityDraftStartedEvent} to the given state. */
  public static void apply(PriorAuthorityState state, PriorAuthorityDraftStartedEvent event) {
    state.submissionId = event.submissionId();
    state.applicationId = event.applicationId();
    state.requestFingerprint = event.requestFingerprint();
    state.status = event.status();
    state.schemaVersion = event.schemaVersion();
  }

  /** Applies a {@link PriorAuthoritySubmittedEvent} to the given state. */
  public static void apply(PriorAuthorityState state, PriorAuthoritySubmittedEvent event) {
    state.submissionId = event.submissionId();
    state.applicationId = event.applicationId();
    state.dataVersion = event.dataVersion();
    state.status = event.status();
  }
}

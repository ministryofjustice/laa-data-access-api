package uk.gov.justice.laa.dstew.access.query.application;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationLinkedEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupRequested;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;

/**
 * Event-fold functions for {@link ApplicationReadModel}. This is the single, canonical source of
 * truth for how each event mutates the persisted-column state of the read model, shared by:
 *
 * <ul>
 *   <li>{@link ApplicationProjection}'s {@code @EventHandler} methods, which fold events onto the
 *       live current-state row as they are published
 *   <li>{@code ApplicationRawReplayService}, which folds events read directly from the raw event
 *       store (bypassing the Axon query API) to reconstruct the same state for diagnostics/recovery
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApplicationReadModelEvolve {

  /** Applies an {@link ApplicationCreatedEvent} to the given read model. */
  public static void apply(ApplicationReadModel model, ApplicationCreatedEvent event) {
    model.setApplicationId(event.applicationId());
    model.setStatus(event.status());
    model.setApplicationDataVersion(event.applicationDataVersion());
    model.setApplicationVersion(0L);
    model.setSchemaVersion(event.schemaVersion());
    model.setApplicationType(event.applicationType());
    model.setApplyApplicationId(event.applyApplicationId());
    model.setLeadApplicationId(event.leadApplicationId());
    model.setCreatedAt(event.occurredAt());
    model.setModifiedAt(event.occurredAt());
  }

  /** Applies an {@link ApplicationLinkedEvent} to the given read model. */
  public static void apply(ApplicationReadModel model, ApplicationLinkedEvent event) {
    model.setLeadApplicationId(event.leadApplicationId());
    model.setModifiedAt(event.occurredAt());
  }

  /** Applies an {@link ApplicationDecisionMadeEvent} to the given read model. */
  public static void apply(ApplicationReadModel model, ApplicationDecisionMadeEvent event) {
    model.setApplicationDataVersion(event.applicationDataVersion());
    model.setApplicationVersion(event.applicationVersion());
    model.setModifiedAt(event.occurredAt());
  }

  /** Applies an {@link ApplicationAssignedToCaseworkerEvent} to the given read model. */
  public static void apply(ApplicationReadModel model, ApplicationAssignedToCaseworkerEvent event) {
    model.setCaseworkerId(event.caseworkerId());
    model.setApplicationVersion(event.applicationVersion());
    model.setApplicationDataVersion(event.applicationDataVersion());
    model.setModifiedAt(event.occurredAt());
  }

  /** Applies an {@link ApplicationUnassignedFromCaseworkerEvent} to the given read model. */
  public static void apply(
      ApplicationReadModel model, ApplicationUnassignedFromCaseworkerEvent event) {
    model.setCaseworkerId(null);
    model.setApplicationVersion(event.applicationVersion());
    model.setApplicationDataVersion(event.applicationDataVersion());
    model.setModifiedAt(event.occurredAt());
  }

  /** Applies a {@link NoteCreatedEvent} to the given read model. */
  public static void apply(ApplicationReadModel model, NoteCreatedEvent event) {
    model.setApplicationDataVersion(event.applicationDataVersion());
    model.setModifiedAt(event.occurredAt());
  }

  /** Applies a {@link LinkedApplicationGroupRequested} to the given read model. */
  public static void apply(ApplicationReadModel model, LinkedApplicationGroupRequested event) {}
}

package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;

/** Builds common application event histories for linked-group tests. */
public final class ApplicationStateEvents {
  private static final Instant OCCURRED_AT = Instant.parse("2026-10-27T12:00:00Z");

  private ApplicationStateEvents() {}

  /** Returns the creation event for an application that is not ready for manual assessment. */
  public static List<Object> notReady(UUID applicationId) {
    return List.of(created(applicationId));
  }

  /** Returns the events for an active, unassigned manual-assessment application. */
  public static List<Object> activeUnassigned(UUID applicationId) {
    return List.of(created(applicationId), ready(applicationId));
  }

  /** Returns the events for an active application assigned to {@code caseworkerId}. */
  public static List<Object> activeAssigned(UUID applicationId, UUID caseworkerId) {
    return List.of(
        created(applicationId),
        ready(applicationId),
        assigned(applicationId, caseworkerId, 1L, 1L));
  }

  /** Returns the events for an automatically granted application. */
  public static List<Object> autoGranted(UUID applicationId) {
    return List.of(
        created(applicationId),
        new ApplicationDecisionMadeEvent(
            applicationId, 1L, 1L, "GRANTED", AutoGrantedState.AUTOGRANTED, OCCURRED_AT));
  }

  /** Returns the events for an active application that already has a decision. */
  public static List<Object> decidedAssigned(UUID applicationId, UUID caseworkerId) {
    return List.of(
        created(applicationId),
        ready(applicationId),
        assigned(applicationId, caseworkerId, 1L, 1L),
        new ApplicationDecisionMadeEvent(
            applicationId, 2L, 2L, "GRANTED", AutoGrantedState.MANUAL, OCCURRED_AT));
  }

  /** Returns events for an assigned application that has been resubmitted and is not ready. */
  public static List<Object> resubmittedAfterAssignment(UUID applicationId, UUID caseworkerId) {
    return List.of(
        created(applicationId),
        ready(applicationId),
        assigned(applicationId, caseworkerId, 1L, 1L),
        new ApplicationUpdatedEvent(
            applicationId, 2L, 2L, "APPLICATION_GRANTED", "APPLICATION_SUBMITTED", OCCURRED_AT));
  }

  /** Folds an application event history into its current state. */
  public static ApplicationState stateOf(List<Object> events) {
    var application = new ApplicationAggregate();
    for (var event : events) {
      switch (event) {
        case ApplicationCreatedEvent created -> application.on(created);
        case ApplicationReadyForManualAssessmentEvent ready -> application.on(ready);
        case ApplicationDecisionMadeEvent decision -> application.on(decision);
        case ApplicationUpdatedEvent updated -> application.on(updated);
        case WorkItemAssigned assigned -> application.on(assigned);
        case WorkItemUnassigned unassigned -> application.on(unassigned);
        default -> throw new IllegalArgumentException("Unsupported application event: " + event);
      }
    }
    return application.getState();
  }

  private static ApplicationCreatedEvent created(UUID applicationId) {
    return new ApplicationCreatedEvent(
        applicationId, 0L, "hash", "APPLICATION_SUBMITTED", 1, OCCURRED_AT, null, null);
  }

  private static ApplicationReadyForManualAssessmentEvent ready(UUID applicationId) {
    return new ApplicationReadyForManualAssessmentEvent(applicationId, 1L, 1L, OCCURRED_AT);
  }

  private static WorkItemAssigned assigned(
      UUID applicationId, UUID caseworkerId, long itemVersion, long assignmentVersion) {
    return new WorkItemAssigned(
        applicationId,
        WorkItemType.APPLICATION,
        itemVersion,
        assignmentVersion,
        caseworkerId,
        OCCURRED_AT);
  }
}

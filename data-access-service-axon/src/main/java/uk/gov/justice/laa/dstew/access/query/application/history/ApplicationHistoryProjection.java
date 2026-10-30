package uk.gov.justice.laa.dstew.access.query.application.history;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ApplicationAssignmentReclaimedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupAssignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupMemberAssignmentInheritedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupUnassignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.config.interceptor.RequestMetadataDispatchInterceptor;
import uk.gov.justice.laa.dstew.access.query.groupassignment.AssignmentChange;
import uk.gov.justice.laa.dstew.access.query.groupassignment.LinkedGroupAssignmentTracker;

/** Independently replayable, append-only audit projection of Application events. */
@Component
@SequencingPolicy
@Namespace("application-history-projection")
@RequiredArgsConstructor
public class ApplicationHistoryProjection {

  private final ApplicationHistoryReadRepository applicationHistoryReadRepository;
  private final ApplicationHistoryAssembler applicationHistoryAssembler;
  private final PriorAuthorityHistoryReadRepository priorAuthorityHistoryReadRepository;
  private final PriorAuthorityHistoryAssembler priorAuthorityHistoryAssembler;
  private final LinkedGroupAssignmentTracker assignmentTracker;
  private final ApplicationHistoryGroupAssignmentStore assignmentStore;

  /** Starts tracking an application as active without writing a history entry. */
  @EventHandler
  public void on(ApplicationReadyForManualAssessmentEvent event) {
    assignmentTracker.applicationReady(
        assignmentStore, event.applicationId(), event.assignmentVersion());
  }

  /** Appends an audit entry when an Application is created. */
  @EventHandler
  public void on(ApplicationCreatedEvent event, EventMessage message) {
    append(
        message,
        event.applicationId(),
        "APPLICATION_CREATED",
        event.applicationDataVersion(),
        event.occurredAt());
  }

  /** Appends an audit entry when an Application is updated. */
  @EventHandler
  public void on(ApplicationUpdatedEvent event, EventMessage message) {
    append(
        message,
        event.applicationId(),
        "APPLICATION_UPDATED",
        event.applicationDataVersion(),
        event.occurredAt());
  }

  /**
   * Appends an audit entry when a linked application group is created.
   *
   * <p>Records {@code APPLICATION_GROUP_CREATED} against the lead application and {@code
   * APPLICATION_GROUP_JOINED} against each non-lead member.
   */
  @EventHandler
  public void on(LinkedApplicationGroupCreatedEvent event, EventMessage message) {
    append(
        message,
        event.leadApplicationId(),
        "APPLICATION_GROUP_CREATED",
        null,
        event.occurredAt(),
        groupHistoryId(message, event.leadApplicationId()));
    event.memberApplicationIds().stream()
        .filter(id -> !id.equals(event.leadApplicationId()))
        .forEach(
            memberId ->
                append(
                    message,
                    memberId,
                    "APPLICATION_GROUP_JOINED",
                    null,
                    event.occurredAt(),
                    groupHistoryId(message, memberId)));
    appendAssignmentChanges(
        assignmentTracker.groupCreated(
            assignmentStore, event.groupId(), event.memberApplicationIds()),
        message,
        event.occurredAt());
  }

  /** Appends an audit entry when an application joins an existing linked application group. */
  @EventHandler
  public void on(MemberAddedToGroupEvent event, EventMessage message) {
    append(
        message,
        event.memberId(),
        "APPLICATION_GROUP_JOINED",
        null,
        event.occurredAt(),
        groupHistoryId(message, event.memberId()));
    appendAssignmentChanges(
        assignmentTracker.memberAdded(assignmentStore, event.groupId(), event.memberId()),
        message,
        event.occurredAt());
  }

  /** Appends a history entry for both applications affected by a linked-group lead change. */
  @EventHandler
  public void on(LinkedApplicationGroupLeadChangedEvent event, EventMessage message) {
    append(
        message,
        event.newLeadApplicationId(),
        "APPLICATION_GROUP_LEAD_CHANGED",
        null,
        event.occurredAt(),
        groupHistoryId(message, event.newLeadApplicationId()));
    append(
        message,
        event.previousLeadApplicationId(),
        "APPLICATION_GROUP_LEAD_CHANGED",
        null,
        event.occurredAt(),
        groupHistoryId(message, event.previousLeadApplicationId()));
  }

  /** Appends a history entry when a non-lead application leaves its linked group. */
  @EventHandler
  public void on(MemberRemovedFromGroupEvent event, EventMessage message) {
    append(
        message,
        event.memberId(),
        "APPLICATION_GROUP_LEFT",
        null,
        event.occurredAt(),
        groupHistoryId(message, event.memberId()));
    assignmentTracker.membersLeft(assignmentStore, event.groupId(), List.of(event.memberId()));
  }

  /** Appends history entries for the member leaving and the dissolution of its former group. */
  @EventHandler
  public void on(LinkedApplicationGroupDissolvedEvent event, EventMessage message) {
    append(
        message,
        event.removedApplicationId(),
        "APPLICATION_GROUP_LEFT",
        null,
        event.occurredAt(),
        groupHistoryId(message, event.removedApplicationId()));
    event.memberApplicationIds().stream()
        .filter(id -> !id.equals(event.removedApplicationId()))
        .forEach(
            applicationId ->
                append(
                    message,
                    applicationId,
                    "APPLICATION_GROUP_DISSOLVED",
                    null,
                    event.occurredAt(),
                    groupHistoryId(message, applicationId)));
    assignmentTracker.membersLeft(assignmentStore, event.groupId(), event.memberApplicationIds());
  }

  /** Appends a thin audit entry for an Application decision. */
  @EventHandler
  public void on(ApplicationDecisionMadeEvent event, EventMessage message) {
    assignmentTracker.applicationDecided(assignmentStore, event.applicationId());
    append(
        message,
        event.applicationId(),
        DecisionValue.GRANTED.name().equals(event.overallDecision())
            ? "APPLICATION_MAKE_DECISION_GRANTED"
            : "APPLICATION_MAKE_DECISION_REFUSED",
        event.applicationDataVersion(),
        event.occurredAt());
  }

  /** Appends a thin audit entry for an application work-list assignment. */
  @EventHandler
  public void on(WorkItemAssigned event, EventMessage message) {
    if (WorkItemType.PRIOR_AUTHORITY.equals(event.workItemType())) {
      return;
    }
    assignmentTracker.directAssignmentChanged(
        assignmentStore, event.workItemId(), event.caseworkerId(), event.assignmentVersion());
    append(
        message, event.workItemId(), "ASSIGN_APPLICATION_TO_CASEWORKER", null, event.occurredAt());
  }

  /** Appends a thin audit entry for an application work-list unassignment. */
  @EventHandler
  public void on(WorkItemUnassigned event, EventMessage message) {
    if (WorkItemType.PRIOR_AUTHORITY.equals(event.workItemType())) {
      return;
    }
    assignmentTracker.directAssignmentChanged(
        assignmentStore, event.workItemId(), null, event.assignmentVersion());
    append(
        message,
        event.workItemId(),
        "UNASSIGN_APPLICATION_TO_CASEWORKER",
        null,
        event.occurredAt());
  }

  /** Appends history for linked-group assignment changes that alter a member's caseworker. */
  @EventHandler
  public void on(LinkedGroupAssignedEvent event, EventMessage message) {
    appendAssignmentChanges(
        assignmentTracker.groupAssignmentChanged(
            assignmentStore, event.groupId(), event.caseworkerId(), event.assignmentVersion()),
        message,
        event.occurredAt());
  }

  /** Appends history for group unassignment changes that alter a member's caseworker. */
  @EventHandler
  public void on(LinkedGroupUnassignedEvent event, EventMessage message) {
    appendAssignmentChanges(
        assignmentTracker.groupAssignmentChanged(
            assignmentStore, event.groupId(), null, event.assignmentVersion()),
        message,
        event.occurredAt());
  }

  /** Appends history when an active member inherits a changed group assignment. */
  @EventHandler
  public void on(LinkedGroupMemberAssignmentInheritedEvent event, EventMessage message) {
    appendAssignmentChanges(
        assignmentTracker
            .memberInherited(
                assignmentStore,
                event.groupId(),
                event.memberApplicationId(),
                event.caseworkerId(),
                event.assignmentVersion())
            .stream()
            .toList(),
        message,
        event.occurredAt());
  }

  /** Appends history when an active member reclaims a changed direct assignment. */
  @EventHandler
  public void on(ApplicationAssignmentReclaimedFromGroupEvent event, EventMessage message) {
    appendAssignmentChanges(
        assignmentTracker
            .assignmentReclaimed(
                assignmentStore,
                event.applicationId(),
                event.caseworkerId(),
                event.assignmentVersion())
            .stream()
            .toList(),
        message,
        event.occurredAt());
  }

  /** Appends a thin audit entry for a note creation. */
  @EventHandler
  public void on(NoteCreatedEvent event, EventMessage message) {
    append(
        message,
        event.applicationId(),
        "APPLICATION_NOTE_CREATED",
        event.applicationDataVersion(),
        event.occurredAt());
  }

  /** Returns chronologically ordered history rows matching the requested public event types. */
  @QueryHandler
  public ApplicationHistoryResult handle(FindApplicationHistoryQuery query) {
    List<ApplicationHistoryReadModel> applicationRows =
        applicationHistoryReadRepository
            .findAllByApplicationIdOrderByOccurredAtAsc(query.applicationId())
            .stream()
            .filter(h -> query.eventTypes().contains(h.getEventType()))
            .toList();
    List<ApplicationHistoryEventResult> applicationEvents =
        applicationHistoryAssembler.assemble(applicationRows);
    List<PriorAuthorityHistoryReadModel> priorAuthorityRows =
        priorAuthorityHistoryReadRepository.findAllByApplicationIdOrderByOccurredAtAsc(
            query.applicationId());
    List<PriorAuthorityHistoryGroupResult> priorAuthorityGroups =
        priorAuthorityHistoryAssembler.assemble(priorAuthorityRows);
    return new ApplicationHistoryResult(applicationEvents, priorAuthorityGroups);
  }

  /** Clears history and tracking tables before replay. */
  @ResetHandler
  public void reset() {
    applicationHistoryReadRepository.deleteAllInBatch();
    priorAuthorityHistoryReadRepository.deleteAllInBatch();
    assignmentStore.deleteAll();
  }

  /** Appends only changed caseworker facts using the existing history event types. */
  private void appendAssignmentChanges(
      List<AssignmentChange> changes, EventMessage message, Instant occurredAt) {
    changes.stream()
        .filter(AssignmentChange::caseworkerChanged)
        .forEach(
            change ->
                applicationHistoryReadRepository.save(
                    ApplicationHistoryReadModel.builder()
                        .eventId(
                            message.identifier() + ":" + change.applicationId() + ":assignment")
                        .applicationId(change.applicationId())
                        .eventType(
                            change.caseworkerId() != null
                                ? "ASSIGN_APPLICATION_TO_CASEWORKER"
                                : "UNASSIGN_APPLICATION_TO_CASEWORKER")
                        .dataVersion(null)
                        .serviceName(RequestMetadataDispatchInterceptor.serviceName(message))
                        .caseworkerId(RequestMetadataDispatchInterceptor.caseworkerId(message))
                        .occurredAt(occurredAt)
                        .build()));
  }

  private void append(
      EventMessage message,
      UUID applicationId,
      String eventType,
      Long dataVersion,
      Instant occurredAt) {
    append(message, applicationId, eventType, dataVersion, occurredAt, message.identifier());
  }

  private void append(
      EventMessage message,
      UUID applicationId,
      String eventType,
      Long dataVersion,
      Instant occurredAt,
      String historyId) {
    applicationHistoryReadRepository.save(
        ApplicationHistoryReadModel.builder()
            .eventId(historyId)
            .applicationId(applicationId)
            .eventType(eventType)
            .dataVersion(dataVersion)
            .serviceName(RequestMetadataDispatchInterceptor.serviceName(message))
            .caseworkerId(RequestMetadataDispatchInterceptor.caseworkerId(message))
            .occurredAt(occurredAt)
            .build());
  }

  private String groupHistoryId(EventMessage message, UUID applicationId) {
    return message.identifier() + ":" + applicationId;
  }
}

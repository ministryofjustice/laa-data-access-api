package uk.gov.justice.laa.dstew.access.command.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ApplicationAssignmentDelegatedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ApplicationAssignmentReclaimedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.DirectGroupWorkItemAssignmentCommand;

/** Event-sourced consistency boundary for an Application and its owned child state. */
@EventSourced(tagKey = "ApplicationAggregate", idType = UUID.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ApplicationAggregate {

  private UUID applicationId;
  private final ApplicationState state = new ApplicationState();

  public UUID getApplicationId() {
    return applicationId;
  }

  public String getOfficeCode() {
    return state.officeCode;
  }

  public ApplicationState getState() {
    return state;
  }

  public boolean isGranted() {
    return "GRANTED".equals(state.overallDecision);
  }

  /** Applies a linked-group assignment to this application, replacing any current assignee. */
  @CommandHandler
  void handle(DirectGroupWorkItemAssignmentCommand command, EventAppender eventAppender) {
    if (applicationId == null
        || state.autoGranted != AutoGrantedState.MANUAL
        || state.overallDecision != null) {
      return;
    }
    if (command.expectedAssignmentVersion() != null
        && command.expectedAssignmentVersion() != state.assignmentVersion) {
      throw new WorkItemAssignmentConflictException(
          command.workItemId(), "the assignment version is stale");
    }
    if (Objects.equals(state.caseworkerId, command.caseworkerId())) {
      return;
    }

    long nextAssignmentVersion = state.assignmentVersion + 1;
    eventAppender.append(
        new WorkItemAssigned(
            command.workItemId(),
            WorkItemType.APPLICATION,
            state.applicationVersion,
            nextAssignmentVersion,
            command.caseworkerId(),
            command.occurredAt()));
  }

  @EventSourcingHandler
  void on(ApplicationCreatedEvent event) {
    ApplicationEvolve.apply(state, event);
    this.applicationId = state.applicationId;
  }

  @EventSourcingHandler
  void on(ApplicationDraftStartedEvent event) {
    ApplicationEvolve.apply(state, event);
    this.applicationId = state.applicationId;
  }

  @EventSourcingHandler
  void on(ApplicationDecisionMadeEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(ApplicationAssignmentDelegatedToGroupEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(ApplicationAssignmentReclaimedFromGroupEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(WorkItemAssigned event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(WorkItemUnassigned event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(NoteCreatedEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(ApplicationReadyForManualAssessmentEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(ApplicationUpdatedEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(ApplicationDocumentUploadedEvent event) {
    ApplicationEvolve.apply(state, event);
  }

  @EntityCreator
  protected ApplicationAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}

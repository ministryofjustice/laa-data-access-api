package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.worklist.DirectPriorAuthorityWorkItemAssignmentCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.DirectPriorAuthorityWorkItemUnassignmentCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;

/**
 * Event-sourced consistency boundary for a PriorAuthority submission.
 *
 * <p>On the first command for this aggregate ID, persists version 0 of the sensitive data and emits
 * {@link PriorAuthorityCreatedEvent}. On an identical retry (same serialised request), it returns
 * with no events. On a conflicting retry (different payload), throws {@link
 * uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException}.
 */
@EventSourced(tagKey = "PriorAuthorityAggregate", idType = UUID.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class PriorAuthorityAggregate {

  private UUID submissionId;
  private final PriorAuthorityState state = new PriorAuthorityState();

  @CommandHandler
  void handle(
      CreatePriorAuthorityCommand command,
      PriorAuthorityDataStore dataStore,
      EventAppender eventAppender) {
    String fingerprint;
    if (submissionId == null) {
      PriorAuthorityDataPayload payload =
          new PriorAuthorityDataPayload(
              command.submissionId(),
              command.applicationId(),
              command.content(),
              command.serialisedRequest(),
              command.occurredAt());
      fingerprint =
          dataStore.append(
              command.submissionId(),
              0L,
              command.applicationId(),
              payload,
              command.serialisedRequest(),
              command.occurredAt());
    } else {
      fingerprint = PayloadFingerprint.compute(command.serialisedRequest());
    }
    PriorAuthorityDecider.decideCreate(state, command, fingerprint)
        .ifPresent(e -> eventAppender.append(e));
  }

  /** Assigns a newly created direct PA work item after durable route resolution. */
  @CommandHandler
  void handle(
      DirectPriorAuthorityWorkItemAssignmentCommand command,
      PriorAuthorityDataStore dataStore,
      EventAppender eventAppender) {
    validateWorkItem(command.workItemId(), command.expectedAssignmentVersion());
    if (state.caseworkerId != null) {
      throw new WorkItemAssignmentConflictException(command.workItemId(), "it is already assigned");
    }
    long nextDataVersion = state.dataVersion + 1;
    dataStore.append(
        submissionId,
        nextDataVersion,
        state.applicationId,
        dataStore.get(submissionId, state.dataVersion).withAssignment(command.eventDescription()),
        command.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(
        new WorkItemAssigned(
            command.workItemId(),
            WorkItemType.PRIOR_AUTHORITY,
            nextDataVersion,
            nextDataVersion,
            state.assignmentVersion + 1,
            command.caseworkerId(),
            command.occurredAt()));
  }

  /** Explicitly clears a direct PA assignment; already-open work is a conflict. */
  @CommandHandler
  void handle(
      DirectPriorAuthorityWorkItemUnassignmentCommand command,
      PriorAuthorityDataStore dataStore,
      EventAppender eventAppender) {
    validateWorkItem(command.workItemId(), command.expectedAssignmentVersion());
    if (state.caseworkerId == null) {
      throw new WorkItemAssignmentConflictException(
          command.workItemId(), "it is already unassigned");
    }
    long nextDataVersion = state.dataVersion + 1;
    dataStore.append(
        submissionId,
        nextDataVersion,
        state.applicationId,
        dataStore.get(submissionId, state.dataVersion).withAssignment(command.eventDescription()),
        command.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(
        new WorkItemUnassigned(
            command.workItemId(),
            WorkItemType.PRIOR_AUTHORITY,
            nextDataVersion,
            nextDataVersion,
            state.assignmentVersion + 1,
            command.occurredAt()));
  }

  private void validateWorkItem(UUID workItemId, long expectedAssignmentVersion) {
    if (submissionId == null || !submissionId.equals(workItemId)) {
      throw new ResourceNotFoundException(
          "No prior-authority work item found with id: " + workItemId);
    }
    if (expectedAssignmentVersion != state.assignmentVersion) {
      throw new WorkItemAssignmentConflictException(workItemId, "the assignment version is stale");
    }
  }

  @EventSourcingHandler
  void on(PriorAuthorityCreatedEvent event) {
    PriorAuthorityEvolve.apply(state, event);
    this.submissionId = state.submissionId;
  }

  @EventSourcingHandler
  void on(WorkItemAssigned event) {
    PriorAuthorityEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(WorkItemUnassigned event) {
    PriorAuthorityEvolve.apply(state, event);
  }

  @EntityCreator
  protected PriorAuthorityAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}

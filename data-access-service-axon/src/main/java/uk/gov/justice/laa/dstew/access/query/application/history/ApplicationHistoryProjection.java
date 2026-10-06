package uk.gov.justice.laa.dstew.access.query.application.history;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.config.interceptor.RequestMetadataDispatchInterceptor;

/** Independently replayable, append-only audit projection of Application events. */
@Component
@Namespace("application-history-projection")
public class ApplicationHistoryProjection {

  private final ApplicationHistoryReadRepository applicationHistoryReadRepository;
  private final ApplicationHistoryAssembler applicationHistoryAssembler;
  private final PriorAuthorityHistoryReadRepository priorAuthorityHistoryReadRepository;
  private final PriorAuthorityHistoryAssembler priorAuthorityHistoryAssembler;

  /** Creates the history projection with its persistence and reconstruction dependencies. */
  public ApplicationHistoryProjection(
      ApplicationHistoryReadRepository applicationHistoryReadRepository,
      ApplicationHistoryAssembler applicationHistoryAssembler,
      PriorAuthorityHistoryReadRepository priorAuthorityHistoryReadRepository,
      PriorAuthorityHistoryAssembler priorAuthorityHistoryAssembler) {
    this.applicationHistoryReadRepository = applicationHistoryReadRepository;
    this.applicationHistoryAssembler = applicationHistoryAssembler;
    this.priorAuthorityHistoryReadRepository = priorAuthorityHistoryReadRepository;
    this.priorAuthorityHistoryAssembler = priorAuthorityHistoryAssembler;
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
  }

  /** Appends a thin audit entry for an Application decision. */
  @EventHandler
  public void on(ApplicationDecisionMadeEvent event, EventMessage message) {
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
    append(
        message, event.workItemId(), "ASSIGN_APPLICATION_TO_CASEWORKER", null, event.occurredAt());
  }

  /** Appends a thin audit entry for an application work-list unassignment. */
  @EventHandler
  public void on(WorkItemUnassigned event, EventMessage message) {
    if (WorkItemType.PRIOR_AUTHORITY.equals(event.workItemType())) {
      return;
    }
    append(
        message,
        event.workItemId(),
        "UNASSIGN_APPLICATION_TO_CASEWORKER",
        null,
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

  @ResetHandler
  public void reset() {
    applicationHistoryReadRepository.deleteAllInBatch();
    priorAuthorityHistoryReadRepository.deleteAllInBatch();
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

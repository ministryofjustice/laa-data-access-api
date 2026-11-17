package uk.gov.justice.laa.dstew.access.query.application.history;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.DecisionValue;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.note.NoteCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.config.interceptor.RequestMetadataDispatchInterceptor;

/**
 * Maps the events in an application's lifecycle stream to history rows, in stream order. Events
 * that are not shown in history, or that concern only another application, give no row. Everything
 * a row needs comes from the events in the stream.
 */
@Component
class ApplicationHistoryMapper {

  /** Application and Prior Authority history rows for one application. */
  record Rows(
      List<ApplicationHistoryRow> application, List<PriorAuthorityHistoryRow> priorAuthority) {}

  /**
   * Maps an application's events to its history rows.
   *
   * @throws ApplicationHistoryIntegrityException if a Prior Authority decision is unsupported, or a
   *     Prior Authority is assigned or unassigned without having been submitted
   */
  Rows map(UUID applicationId, List<EventMessage> events) {
    var rows = new Rows(new ArrayList<>(), new ArrayList<>());
    Map<UUID, String> submittedTypes = submittedTypes(applicationId, events);
    events.forEach(message -> add(applicationId, message, rows, submittedTypes));
    return new Rows(List.copyOf(rows.application()), List.copyOf(rows.priorAuthority()));
  }

  /**
   * Prior Authority types by Prior Authority, as submitted; a work item exists only once submitted.
   */
  private static Map<UUID, String> submittedTypes(UUID id, List<EventMessage> events) {
    Map<UUID, String> types = new HashMap<>();
    events.stream()
        .map(EventMessage::payload)
        .forEach(
            payload -> {
              if (payload instanceof PriorAuthoritySubmittedEvent event
                  && id.equals(event.applicationId())) {
                types.put(event.priorAuthorityId(), event.priorAuthorityType());
              }
            });
    return types;
  }

  private static void add(
      UUID id, EventMessage message, Rows rows, Map<UUID, String> submittedTypes) {
    switch (message.payload()) {
      case ApplicationCreatedEvent event when id.equals(event.applicationId()) ->
          application(
              rows,
              message,
              id,
              "APPLICATION_CREATED",
              event.applicationDataVersion(),
              null,
              event.occurredAt());
      case ApplicationUpdatedEvent event when id.equals(event.applicationId()) ->
          application(
              rows,
              message,
              id,
              "APPLICATION_UPDATED",
              event.applicationDataVersion(),
              null,
              event.occurredAt());
      case ApplicationDecisionMadeEvent event when id.equals(event.applicationId()) ->
          application(
              rows,
              message,
              id,
              DecisionValue.GRANTED.name().equals(event.overallDecision())
                  ? "APPLICATION_MAKE_DECISION_GRANTED"
                  : "APPLICATION_MAKE_DECISION_REFUSED",
              event.applicationDataVersion(),
              null,
              event.occurredAt());
      case NoteCreatedEvent event when id.equals(event.applicationId()) ->
          application(
              rows,
              message,
              id,
              "APPLICATION_NOTE_CREATED",
              event.applicationDataVersion(),
              null,
              event.occurredAt());
      case LinkedApplicationGroupCreatedEvent event -> {
        if (id.equals(event.leadApplicationId())) {
          group(
              rows,
              message,
              id,
              "APPLICATION_GROUP_CREATED",
              event.justificationId(),
              event.occurredAt());
        } else if (event.memberApplicationIds().contains(id)) {
          group(
              rows,
              message,
              id,
              "APPLICATION_GROUP_JOINED",
              event.justificationId(),
              event.occurredAt());
        }
      }
      case MemberAddedToGroupEvent event -> {
        if (id.equals(event.memberId())) {
          group(
              rows,
              message,
              id,
              "APPLICATION_GROUP_JOINED",
              event.justificationId(),
              event.occurredAt());
        } else if (contains(event.memberApplicationIds(), id)) {
          group(
              rows,
              message,
              id,
              "APPLICATION_GROUP_MEMBER_ADDED",
              event.justificationId(),
              event.occurredAt());
        }
      }
      case LinkedApplicationGroupLeadChangedEvent event -> {
        // Events stored before the group's members were recorded cover only the two leads.
        var members =
            event.memberApplicationIds() == null
                ? List.of(event.newLeadApplicationId(), event.previousLeadApplicationId())
                : event.memberApplicationIds();
        if (members.contains(id)) {
          group(
              rows,
              message,
              id,
              "APPLICATION_GROUP_LEAD_CHANGED",
              event.justificationId(),
              event.occurredAt());
        }
      }
      case MemberRemovedFromGroupEvent event -> {
        if (id.equals(event.memberId())) {
          group(
              rows,
              message,
              id,
              "APPLICATION_GROUP_LEFT",
              event.justificationId(),
              event.occurredAt());
        } else if (contains(event.memberApplicationIds(), id)) {
          group(
              rows,
              message,
              id,
              "APPLICATION_GROUP_MEMBER_REMOVED",
              event.justificationId(),
              event.occurredAt());
        }
      }
      case LinkedApplicationGroupDissolvedEvent event -> {
        if (id.equals(event.removedApplicationId())) {
          group(
              rows,
              message,
              id,
              "APPLICATION_GROUP_LEFT",
              event.justificationId(),
              event.occurredAt());
        } else if (event.memberApplicationIds().contains(id)) {
          group(
              rows,
              message,
              id,
              "APPLICATION_GROUP_DISSOLVED",
              event.justificationId(),
              event.occurredAt());
        }
      }
      case WorkItemAssigned event ->
          workItem(
              rows,
              message,
              id,
              event.workItemType(),
              event.workItemId(),
              event.applicationId(),
              event.itemVersion(),
              "ASSIGN_APPLICATION_TO_CASEWORKER",
              event.occurredAt(),
              submittedTypes);
      case WorkItemUnassigned event ->
          workItem(
              rows,
              message,
              id,
              event.workItemType(),
              event.workItemId(),
              event.applicationId(),
              event.itemVersion(),
              "UNASSIGN_APPLICATION_TO_CASEWORKER",
              event.occurredAt(),
              submittedTypes);
      case PriorAuthoritySubmittedEvent event when id.equals(event.applicationId()) ->
          priorAuthority(
              rows,
              message,
              id,
              event.priorAuthorityId(),
              event.priorAuthorityType(),
              "PRIOR_AUTHORITY_SUBMITTED",
              event.dataVersion(),
              event.occurredAt());
      case PriorAuthorityDecisionMadeEvent event when id.equals(event.applicationId()) ->
          priorAuthority(
              rows,
              message,
              id,
              event.priorAuthorityId(),
              event.priorAuthorityType(),
              decisionEventType(event),
              event.dataVersion(),
              event.occurredAt());
      default -> {
        // Not shown in this application's history.
      }
    }
  }

  private static void workItem(
      Rows rows,
      EventMessage message,
      UUID id,
      WorkItemType type,
      UUID workItemId,
      UUID parentApplicationId,
      long itemVersion,
      String eventType,
      Instant occurredAt,
      Map<UUID, String> submittedTypes) {
    if (!WorkItemType.PRIOR_AUTHORITY.equals(type)) {
      if (id.equals(workItemId)) {
        application(rows, message, id, eventType, null, null, occurredAt);
      }
      return;
    }
    if (!id.equals(parentApplicationId)) {
      return;
    }
    if (!submittedTypes.containsKey(workItemId)) {
      throw new ApplicationHistoryIntegrityException(
          id, workItemId, eventType + " for a prior authority that was never submitted");
    }
    priorAuthority(
        rows,
        message,
        id,
        workItemId,
        submittedTypes.get(workItemId),
        eventType,
        itemVersion,
        occurredAt);
  }

  private static String decisionEventType(PriorAuthorityDecisionMadeEvent event) {
    if (DecisionValue.GRANTED.name().equals(event.overallDecision())) {
      return "PRIOR_AUTHORITY_MAKE_DECISION_GRANTED";
    }
    if (DecisionValue.REFUSED.name().equals(event.overallDecision())) {
      return "PRIOR_AUTHORITY_MAKE_DECISION_REFUSED";
    }
    throw new ApplicationHistoryIntegrityException(
        event.applicationId(),
        event.priorAuthorityId(),
        "unsupported prior-authority decision value: " + event.overallDecision());
  }

  private static boolean contains(List<UUID> ids, UUID id) {
    return ids != null && ids.contains(id);
  }

  private static void group(
      Rows rows,
      EventMessage message,
      UUID id,
      String eventType,
      UUID justificationId,
      Instant occurredAt) {
    application(rows, message, id, eventType, null, justificationId, occurredAt);
  }

  private static void application(
      Rows rows,
      EventMessage message,
      UUID id,
      String eventType,
      Long dataVersion,
      UUID justificationId,
      Instant occurredAt) {
    rows.application()
        .add(
            ApplicationHistoryRow.builder()
                .eventId(message.identifier())
                .applicationId(id)
                .eventType(eventType)
                .dataVersion(dataVersion)
                .justificationId(justificationId)
                .serviceName(RequestMetadataDispatchInterceptor.serviceName(message))
                .caseworkerId(RequestMetadataDispatchInterceptor.caseworkerId(message))
                .occurredAt(occurredAt)
                .build());
  }

  private static void priorAuthority(
      Rows rows,
      EventMessage message,
      UUID id,
      UUID priorAuthorityId,
      String priorAuthorityType,
      String eventType,
      long itemVersion,
      Instant occurredAt) {
    rows.priorAuthority()
        .add(
            PriorAuthorityHistoryRow.builder()
                .eventId(message.identifier())
                .applicationId(id)
                .priorAuthorityId(priorAuthorityId)
                .priorAuthorityType(priorAuthorityType)
                .eventType(eventType)
                .itemVersion(itemVersion)
                .serviceName(RequestMetadataDispatchInterceptor.serviceName(message))
                .caseworkerId(RequestMetadataDispatchInterceptor.caseworkerId(message))
                .occurredAt(occurredAt)
                .build());
  }
}

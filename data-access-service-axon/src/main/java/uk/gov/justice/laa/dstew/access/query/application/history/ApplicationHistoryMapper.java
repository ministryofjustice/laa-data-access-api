package uk.gov.justice.laa.dstew.access.query.application.history;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.EventMessage;
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
 * Maps one application's events, in stream order, to its history. It holds no state between
 * requests; a Prior Authority's type for assignment entries comes from its submitted event in the
 * same stream. Descriptions are read from event content, so erased content gives no description.
 */
final class ApplicationHistoryMapper {

  private final UUID applicationId;
  private final List<ApplicationHistoryEventResult> applicationEvents = new ArrayList<>();
  private final Map<UUID, List<PriorAuthorityEntry>> priorAuthorityEntries = new LinkedHashMap<>();
  private final Map<UUID, String> submittedTypes = new HashMap<>();

  private ApplicationHistoryMapper(UUID applicationId) {
    this.applicationId = applicationId;
  }

  /**
   * Maps events to history, keeping application entries whose type is requested.
   *
   * @throws ApplicationHistoryIntegrityException if a Prior Authority has conflicting types or an
   *     unsupported decision
   */
  static ApplicationHistoryResult map(
      UUID applicationId, List<EventMessage> events, List<String> eventTypes) {
    var mapper = new ApplicationHistoryMapper(applicationId);
    events.stream()
        .map(EventMessage::payload)
        .filter(PriorAuthoritySubmittedEvent.class::isInstance)
        .map(PriorAuthoritySubmittedEvent.class::cast)
        .forEach(
            event ->
                mapper.submittedTypes.put(event.priorAuthorityId(), event.priorAuthorityType()));
    events.forEach(mapper::add);
    return new ApplicationHistoryResult(
        mapper.applicationEvents.stream()
            .filter(entry -> eventTypes.contains(entry.eventType()))
            .toList(),
        mapper.priorAuthorityGroups());
  }

  private void add(EventMessage message) {
    switch (message.payload()) {
      case ApplicationCreatedEvent event ->
          application(message, "APPLICATION_CREATED", event.occurredAt(), null);
      case ApplicationUpdatedEvent event ->
          application(message, "APPLICATION_UPDATED", event.occurredAt(), null);
      case LinkedApplicationGroupCreatedEvent event -> {
        if (applicationId.equals(event.leadApplicationId())) {
          application(message, "APPLICATION_GROUP_CREATED", event.occurredAt(), null);
        } else if (event.memberApplicationIds().contains(applicationId)) {
          application(message, "APPLICATION_GROUP_JOINED", event.occurredAt(), null);
        }
      }
      case MemberAddedToGroupEvent event -> {
        if (applicationId.equals(event.memberId())) {
          application(message, "APPLICATION_GROUP_JOINED", event.occurredAt(), null);
        }
      }
      case LinkedApplicationGroupLeadChangedEvent event -> {
        if (applicationId.equals(event.previousLeadApplicationId())
            || applicationId.equals(event.newLeadApplicationId())) {
          application(message, "APPLICATION_GROUP_LEAD_CHANGED", event.occurredAt(), null);
        }
      }
      case MemberRemovedFromGroupEvent event -> {
        if (applicationId.equals(event.memberId())) {
          application(message, "APPLICATION_GROUP_LEFT", event.occurredAt(), null);
        }
      }
      case LinkedApplicationGroupDissolvedEvent event -> {
        if (applicationId.equals(event.removedApplicationId())) {
          application(message, "APPLICATION_GROUP_LEFT", event.occurredAt(), null);
        } else if (event.memberApplicationIds().contains(applicationId)) {
          application(message, "APPLICATION_GROUP_DISSOLVED", event.occurredAt(), null);
        }
      }
      case ApplicationDecisionMadeEvent event ->
          application(
              message,
              DecisionValue.GRANTED.name().equals(event.overallDecision())
                  ? "APPLICATION_MAKE_DECISION_GRANTED"
                  : "APPLICATION_MAKE_DECISION_REFUSED",
              event.occurredAt(),
              event.eventDescription().orElse(null));
      case NoteCreatedEvent event ->
          application(
              message,
              "APPLICATION_NOTE_CREATED",
              event.occurredAt(),
              event.noteText().orElse(null));
      case WorkItemAssigned event ->
          workItem(
              message,
              event.workItemType(),
              event.workItemId(),
              "ASSIGN_APPLICATION_TO_CASEWORKER",
              event.occurredAt());
      case WorkItemUnassigned event ->
          workItem(
              message,
              event.workItemType(),
              event.workItemId(),
              "UNASSIGN_APPLICATION_TO_CASEWORKER",
              event.occurredAt());
      case PriorAuthoritySubmittedEvent event ->
          priorAuthority(
              message,
              event.priorAuthorityId(),
              event.priorAuthorityType(),
              "PRIOR_AUTHORITY_SUBMITTED",
              event.occurredAt(),
              null);
      case PriorAuthorityDecisionMadeEvent event ->
          priorAuthority(
              message,
              event.priorAuthorityId(),
              event.priorAuthorityType(),
              decisionEventType(event),
              event.occurredAt(),
              decisionDescription(event));
      default -> {
        // Not shown in history.
      }
    }
  }

  private void workItem(
      EventMessage message,
      WorkItemType type,
      UUID workItemId,
      String eventType,
      Instant occurredAt) {
    if (WorkItemType.PRIOR_AUTHORITY.equals(type)) {
      String priorAuthorityType = submittedTypes.get(workItemId);
      if (priorAuthorityType != null) {
        priorAuthority(message, workItemId, priorAuthorityType, eventType, occurredAt, null);
      }
    } else if (applicationId.equals(workItemId)) {
      application(message, eventType, occurredAt, null);
    }
  }

  private void application(
      EventMessage message, String eventType, Instant occurredAt, String description) {
    applicationEvents.add(
        new ApplicationHistoryEventResult(
            applicationId,
            eventType,
            occurredAt,
            RequestMetadataDispatchInterceptor.serviceName(message),
            description,
            RequestMetadataDispatchInterceptor.caseworkerId(message)));
  }

  private void priorAuthority(
      EventMessage message,
      UUID priorAuthorityId,
      String priorAuthorityType,
      String eventType,
      Instant occurredAt,
      String description) {
    priorAuthorityEntries
        .computeIfAbsent(priorAuthorityId, ignored -> new ArrayList<>())
        .add(
            new PriorAuthorityEntry(
                priorAuthorityType,
                new PriorAuthorityHistoryEventResult(
                    eventType,
                    occurredAt,
                    RequestMetadataDispatchInterceptor.serviceName(message),
                    description,
                    RequestMetadataDispatchInterceptor.caseworkerId(message))));
  }

  private List<PriorAuthorityHistoryGroupResult> priorAuthorityGroups() {
    return priorAuthorityEntries.entrySet().stream()
        .map(
            group -> {
              Set<String> types = new LinkedHashSet<>();
              group.getValue().forEach(entry -> types.add(entry.priorAuthorityType()));
              if (types.size() > 1) {
                throw new ApplicationHistoryIntegrityException(
                    applicationId,
                    group.getKey(),
                    "conflicting priorAuthorityType values: " + types);
              }
              return new PriorAuthorityHistoryGroupResult(
                  group.getKey(),
                  types.iterator().next(),
                  group.getValue().stream().map(PriorAuthorityEntry::event).toList());
            })
        .toList();
  }

  private String decisionEventType(PriorAuthorityDecisionMadeEvent event) {
    if (DecisionValue.GRANTED.name().equals(event.overallDecision())) {
      return "PRIOR_AUTHORITY_MAKE_DECISION_GRANTED";
    }
    if (DecisionValue.REFUSED.name().equals(event.overallDecision())) {
      return "PRIOR_AUTHORITY_MAKE_DECISION_REFUSED";
    }
    throw new ApplicationHistoryIntegrityException(
        applicationId,
        event.priorAuthorityId(),
        "unsupported prior-authority decision value: " + event.overallDecision());
  }

  private static String decisionDescription(PriorAuthorityDecisionMadeEvent event) {
    String outcome =
        DecisionValue.GRANTED.name().equals(event.overallDecision()) ? "Granted" : "Refused";
    String description = "Outcome: " + outcome + ", Decision at: " + event.occurredAt();
    String justification = event.decisionJustification().orElse(null);
    if (justification == null || justification.isBlank()) {
      return description;
    }
    return description + ", Justification: " + justification;
  }

  private record PriorAuthorityEntry(
      String priorAuthorityType, PriorAuthorityHistoryEventResult event) {}
}

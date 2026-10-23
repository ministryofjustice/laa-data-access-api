package uk.gov.justice.laa.dstew.access.query.worklist;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ApplicationAssignmentReclaimedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupAssignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupMemberAssignmentInheritedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.LinkedGroupUnassignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.query.groupassignment.AssignmentChange;
import uk.gov.justice.laa.dstew.access.query.groupassignment.LinkedGroupAssignmentTracker;

/** Replayable projection of active work; it is never consulted to route a command. */
@Component
@Namespace("work-list-projection")
@SequencingPolicy
@Slf4j
@RequiredArgsConstructor
public class WorkListProjection {
  private final WorkListItemReadRepository items;
  private final ApplicationDataStore applicationDataStore;
  private final PriorAuthorityDataStore priorAuthorityDataStore;
  private final LinkedGroupAssignmentTracker assignmentTracker;
  private final WorkListGroupAssignmentStore assignmentStore;

  /** Returns a database-filtered page of active work, oldest submission first. */
  @QueryHandler
  public FindWorkListItemsResult handle(FindWorkListItemsQuery query) {
    Page<WorkListItemReadModel> page =
        items.findAll(
            WorkListItemSpecification.from(query),
            PageRequest.of(
                query.page() - 1,
                query.pageSize(),
                Sort.by(Sort.Direction.ASC, "submittedAt")
                    .and(Sort.by(Sort.Direction.ASC, "itemType"))
                    .and(Sort.by(Sort.Direction.ASC, "id"))));
    return new FindWorkListItemsResult(
        page.getContent(), page.getTotalElements(), query.page(), query.pageSize());
  }

  /** A manual-assessment outcome activates one application work item. */
  @EventHandler
  public void on(ApplicationReadyForManualAssessmentEvent event, EventMessage message) {
    ApplicationDataPayload data =
        applicationDataStore.get(event.applicationId(), event.applicationDataVersion());
    WorkListItemReadModel item =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION,
            event.applicationId(),
            null,
            event.occurredAt(),
            event.applicationVersion(),
            message.identifier().hashCode());
    populateApplicationFields(item, data);
    item.setApplicationStatus("APPLICATION_SUBMITTED");
    AssignmentChange assignment =
        assignmentTracker.applicationReady(
            assignmentStore, event.applicationId(), event.assignmentVersion());
    item.applyAssignment(
        assignment.groupId(), assignment.caseworkerId(), assignment.assignmentVersion());
    items.save(item);
  }

  /** PA submission directly activates one PA work item under its parent application. */
  @EventHandler
  public void on(PriorAuthoritySubmittedEvent event, EventMessage message) {
    ApplicationDataPayload parentData =
        applicationDataStore.get(event.applicationId(), event.applicationDataVersion());
    PriorAuthorityDataPayload priorAuthorityData =
        priorAuthorityDataStore.get(event.priorAuthorityId(), event.dataVersion());
    WorkListItemReadModel item =
        new WorkListItemReadModel(
            WorkItemType.PRIOR_AUTHORITY,
            event.priorAuthorityId(),
            event.applicationId(),
            event.occurredAt(),
            event.dataVersion(),
            message.identifier().hashCode());
    populateApplicationFields(item, parentData);
    String priorAuthorityType = priorAuthorityData.content().priorAuthorityType().name();
    item.setPriorAuthorityType(priorAuthorityType);
    item.setExpertType(
        "EXPERT".equals(priorAuthorityType)
            ? priorAuthorityData.content().expertDetails().expertType()
            : null);
    items.save(item);
  }

  /** A terminal application decision removes only its application work row. */
  @EventHandler
  public void on(ApplicationDecisionMadeEvent event) {
    items.deleteById(event.applicationId());
    assignmentTracker.applicationDecided(assignmentStore, event.applicationId());
  }

  /** A terminal prior-authority decision removes its prior-authority work row. */
  @EventHandler
  public void on(PriorAuthorityDecisionMadeEvent event) {
    items.deleteById(event.priorAuthorityId());
  }

  /** Applies a generic direct assignment to the event's immutable work-item identity. */
  @EventHandler
  public void on(WorkItemAssigned event, EventMessage message) {
    Optional<AssignmentChange> assignment =
        event.workItemType() == WorkItemType.APPLICATION
            ? Optional.of(
                assignmentTracker.directAssignmentChanged(
                    assignmentStore,
                    event.workItemId(),
                    event.caseworkerId(),
                    event.assignmentVersion()))
            : Optional.empty();
    items
        .findById(event.workItemId())
        .ifPresentOrElse(
            item -> {
              requireMatchingType(item, event.workItemType(), event.workItemId());
              if (event.workItemType() == WorkItemType.APPLICATION) {
                assignment.ifPresent(
                    change ->
                        item.applyAssignment(
                            change.groupId(), change.caseworkerId(), change.assignmentVersion()));
              } else {
                item.setAssigneeId(event.caseworkerId());
                item.setAssignmentVersion(event.assignmentVersion());
              }
              item.setItemVersion(event.itemVersion());
              item.setUpdatedAt(event.occurredAt());
              item.setProjectionPosition(message.identifier().hashCode());
              items.save(item);
            },
            () ->
                log.warn(
                    "Cannot assign missing work-list item: id={}, type={}",
                    event.workItemId(),
                    event.workItemType()));
  }

  /** Applies a generic direct unassignment to the event's immutable work-item identity. */
  @EventHandler
  public void on(WorkItemUnassigned event, EventMessage message) {
    Optional<AssignmentChange> assignment =
        event.workItemType() == WorkItemType.APPLICATION
            ? Optional.of(
                assignmentTracker.directAssignmentChanged(
                    assignmentStore, event.workItemId(), null, event.assignmentVersion()))
            : Optional.empty();
    items
        .findById(event.workItemId())
        .ifPresentOrElse(
            item -> {
              requireMatchingType(item, event.workItemType(), event.workItemId());
              if (event.workItemType() == WorkItemType.APPLICATION) {
                assignment.ifPresent(
                    change ->
                        item.applyAssignment(
                            change.groupId(), change.caseworkerId(), change.assignmentVersion()));
              } else {
                item.setAssigneeId(null);
                item.setAssignmentVersion(event.assignmentVersion());
              }
              item.setItemVersion(event.itemVersion());
              item.setUpdatedAt(event.occurredAt());
              item.setProjectionPosition(message.identifier().hashCode());
              items.save(item);
            },
            () ->
                log.warn(
                    "Cannot unassign missing work-list item: id={}, type={}",
                    event.workItemId(),
                    event.workItemType()));
  }

  /** Applies a new group assignment to each matching active application row. */
  @EventHandler
  public void on(LinkedGroupAssignedEvent event, EventMessage message) {
    applyAssignmentChanges(
        assignmentTracker.groupAssignmentChanged(
            assignmentStore, event.groupId(), event.caseworkerId(), event.assignmentVersion()),
        event.occurredAt(),
        message);
  }

  /** Clears the group assignee from matching active application rows. */
  @EventHandler
  public void on(LinkedGroupUnassignedEvent event, EventMessage message) {
    applyAssignmentChanges(
        assignmentTracker.groupAssignmentChanged(
            assignmentStore, event.groupId(), null, event.assignmentVersion()),
        event.occurredAt(),
        message);
  }

  /** Applies an inherited group assignment to an eligible work-list row. */
  @EventHandler
  public void on(LinkedGroupMemberAssignmentInheritedEvent event, EventMessage message) {
    applyAssignmentChanges(
        assignmentTracker
            .memberInherited(
                assignmentStore,
                event.groupId(),
                event.memberApplicationId(),
                event.caseworkerId(),
                event.assignmentVersion())
            .stream()
            .toList(),
        event.occurredAt(),
        message);
  }

  /** Returns a reclaimed application work item to its direct assignment boundary. */
  @EventHandler
  public void on(ApplicationAssignmentReclaimedFromGroupEvent event, EventMessage message) {
    applyAssignmentChanges(
        assignmentTracker
            .assignmentReclaimed(
                assignmentStore,
                event.applicationId(),
                event.caseworkerId(),
                event.assignmentVersion())
            .stream()
            .toList(),
        event.occurredAt(),
        message);
  }

  /** Tracks the members of a newly created group and applies any active assignment changes. */
  @EventHandler
  public void on(LinkedApplicationGroupCreatedEvent event, EventMessage message) {
    applyAssignmentChanges(
        assignmentTracker.groupCreated(
            assignmentStore, event.groupId(), event.memberApplicationIds()),
        event.occurredAt(),
        message);
  }

  /** Tracks a newly joined member and applies its inherited group assignment if active. */
  @EventHandler
  public void on(MemberAddedToGroupEvent event, EventMessage message) {
    applyAssignmentChanges(
        assignmentTracker.memberAdded(assignmentStore, event.groupId(), event.memberId()),
        event.occurredAt(),
        message);
  }

  /** Releases membership tracking for a removed application. */
  @EventHandler
  public void on(MemberRemovedFromGroupEvent event) {
    assignmentTracker.membersLeft(assignmentStore, event.groupId(), List.of(event.memberId()));
  }

  /** Releases all membership tracking when the group dissolves. */
  @EventHandler
  public void on(LinkedApplicationGroupDissolvedEvent event) {
    assignmentTracker.membersLeft(assignmentStore, event.groupId(), event.memberApplicationIds());
  }

  /** Applies tracker changes to existing application work-list rows only. */
  private void applyAssignmentChanges(
      List<AssignmentChange> changes, Instant occurredAt, EventMessage message) {
    changes.forEach(
        change ->
            items
                .findById(change.applicationId())
                .filter(item -> item.getItemType() == WorkItemType.APPLICATION)
                .ifPresent(
                    item -> {
                      item.applyAssignment(
                          change.groupId(), change.caseworkerId(), change.assignmentVersion());
                      item.setUpdatedAt(occurredAt);
                      item.setProjectionPosition(message.identifier().hashCode());
                      items.save(item);
                    }));
  }

  /** Deletes all disposable rows before event-stream replay. */
  @ResetHandler
  public void reset() {
    items.deleteAllInBatch();
    assignmentStore.deleteAll();
  }

  private void requireMatchingType(
      WorkListItemReadModel item, WorkItemType eventType, UUID workItemId) {
    if (item.getItemType() != eventType) {
      throw new IllegalStateException("Work item type mismatch for " + workItemId);
    }
  }

  private void populateApplicationFields(WorkListItemReadModel item, ApplicationDataPayload data) {
    record MatterPair(String name, String code) {}

    item.setLaaReference(data.laaReference());
    item.setCategoryOfLaw(data.categoryOfLaw());
    item.setCategoryOfLawCode(data.categoryOfLawCode());
    item.setUsedDelegatedFunctions(data.usedDelegatedFunctions());
    List<MatterPair> matterPairs =
        (data.proceedings() == null ? Stream.<Proceeding>empty() : data.proceedings().stream())
            .filter(Objects::nonNull)
            .map(
                proceeding ->
                    new MatterPair(proceeding.getMatterType(), proceeding.getMatterTypeCode()))
            .distinct()
            .toList();
    item.setMatterTypes(matterPairs.stream().map(MatterPair::name).toList());
    item.setMatterTypeCodes(matterPairs.stream().map(MatterPair::code).toList());
  }
}

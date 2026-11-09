package uk.gov.justice.laa.dstew.access.query.worklist;

import java.util.LinkedHashSet;
import java.util.UUID;
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
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.query.application.summary.ApplicationSummaries;

/**
 * Replayable projection of active work; it is never consulted to route a command.
 *
 * <p>Events are handled in one sequence so linked-group membership is applied in order with the
 * readiness events that create application rows.
 */
@Slf4j
@Component
@SequencingPolicy
@Namespace("work-list-projection")
public class WorkListProjection {
  private final WorkListItemReadRepository items;
  private final WorkListGroupMemberReadRepository groupMembers;
  private final PriorAuthorityDataStore priorAuthorityDataStore;
  private final ApplicationSummaries applicationSummaries;

  /** Creates a projection backed by its read repositories and the application summary port. */
  public WorkListProjection(
      WorkListItemReadRepository items,
      WorkListGroupMemberReadRepository groupMembers,
      PriorAuthorityDataStore priorAuthorityDataStore,
      ApplicationSummaries applicationSummaries) {
    this.items = items;
    this.groupMembers = groupMembers;
    this.priorAuthorityDataStore = priorAuthorityDataStore;
    this.applicationSummaries = applicationSummaries;
  }

  /**
   * Returns a database-filtered page of active work, oldest submission first. Application facts
   * come from the application summary rather than copies on the work item.
   */
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
    var summaries =
        applicationSummaries.findByIds(
            page.getContent().stream()
                .map(WorkListItemReadModel::subjectApplicationId)
                .distinct()
                .toList());
    return new FindWorkListItemsResult(
        page.getContent(), summaries, page.getTotalElements(), query.page(), query.pageSize());
  }

  /** Creates an application work item as soon as the application is submitted. */
  @EventHandler
  public void on(ApplicationCreatedEvent event, EventMessage message) {
    WorkListItemReadModel item =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION,
            event.applicationId(),
            null,
            event.occurredAt(),
            0L,
            message.identifier().hashCode());
    item.setAutoGranted(AutoGrantedState.PENDING);
    item.setApplicationStatus(event.status());
    items.save(item);
  }

  /** A manual-assessment outcome updates an existing application work item. */
  @EventHandler
  public void on(ApplicationReadyForManualAssessmentEvent event, EventMessage message) {
    WorkListItemReadModel item =
        items
            .findById(event.applicationId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Missing application work-list item for " + event.applicationId()));
    item.setReadyAt(event.occurredAt());
    item.setAutoGranted(AutoGrantedState.MANUAL);
    item.setApplicationStatus("APPLICATION_SUBMITTED");
    item.setItemVersion(event.applicationVersion());
    item.setUpdatedAt(event.occurredAt());
    item.setProjectionPosition(message.identifier().hashCode());
    groupMembers
        .findById(event.applicationId())
        .ifPresent(member -> item.useLinkedGroupBoundary(member.getGroupId()));
    items.save(item);
  }

  /** PA submission directly activates one PA work item under its parent application. */
  @EventHandler
  public void on(PriorAuthoritySubmittedEvent event, EventMessage message) {
    WorkListItemReadModel item =
        new WorkListItemReadModel(
            WorkItemType.PRIOR_AUTHORITY,
            event.priorAuthorityId(),
            event.applicationId(),
            event.occurredAt(),
            event.dataVersion(),
            message.identifier().hashCode());
    item.setReadyAt(event.occurredAt());
    PriorAuthorityDataPayload priorAuthorityData =
        priorAuthorityDataStore.get(event.priorAuthorityId(), event.dataVersion());
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
  }

  /** A terminal prior-authority decision removes its prior-authority work row. */
  @EventHandler
  public void on(PriorAuthorityDecisionMadeEvent event) {
    items.deleteById(event.priorAuthorityId());
  }

  /** Applies a generic direct assignment to the event's immutable work-item identity. */
  @EventHandler
  public void on(WorkItemAssigned event, EventMessage message) {
    items
        .findById(event.workItemId())
        .ifPresentOrElse(
            item -> {
              requireMatchingType(item, event.workItemType(), event.workItemId());
              item.setAssigneeId(event.caseworkerId());
              item.setItemVersion(event.itemVersion());
              item.setAssignmentVersion(event.assignmentVersion());
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
    items
        .findById(event.workItemId())
        .ifPresentOrElse(
            item -> {
              requireMatchingType(item, event.workItemType(), event.workItemId());
              item.setAssigneeId(null);
              item.setItemVersion(event.itemVersion());
              item.setAssignmentVersion(event.assignmentVersion());
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

  /** Makes every member of a newly established linked group share the group boundary. */
  @EventHandler
  public void on(LinkedApplicationGroupCreatedEvent event, EventMessage message) {
    event.memberApplicationIds().forEach(memberId -> joinGroup(memberId, event.groupId(), message));
  }

  /** Makes an application joining a linked group share the group boundary. */
  @EventHandler
  public void on(MemberAddedToGroupEvent event, EventMessage message) {
    joinGroup(event.memberId(), event.groupId(), message);
  }

  /** Returns an application leaving a linked group to its own direct boundary. */
  @EventHandler
  public void on(MemberRemovedFromGroupEvent event, EventMessage message) {
    leaveGroup(event.memberId(), message);
  }

  /** Returns every remaining member of a dissolved group to its own direct boundary. */
  @EventHandler
  public void on(LinkedApplicationGroupDissolvedEvent event, EventMessage message) {
    var memberIds = new LinkedHashSet<UUID>(event.memberApplicationIds());
    memberIds.add(event.removedApplicationId());
    groupMembers.findAllByGroupId(event.groupId()).stream()
        .map(WorkListGroupMemberReadModel::getApplicationId)
        .forEach(memberIds::add);
    memberIds.forEach(memberId -> leaveGroup(memberId, message));
  }

  /** Deletes all disposable rows before event-stream replay. */
  @ResetHandler
  public void reset() {
    items.deleteAllInBatch();
    groupMembers.deleteAllInBatch();
  }

  private void joinGroup(UUID applicationId, UUID groupId, EventMessage message) {
    groupMembers.save(new WorkListGroupMemberReadModel(applicationId, groupId));
    items
        .findById(applicationId)
        .filter(item -> item.getItemType() == WorkItemType.APPLICATION)
        .ifPresent(
            item -> {
              item.useLinkedGroupBoundary(groupId);
              item.setProjectionPosition(message.identifier().hashCode());
              items.save(item);
            });
  }

  private void leaveGroup(UUID applicationId, EventMessage message) {
    groupMembers.deleteById(applicationId);
    items
        .findById(applicationId)
        .filter(item -> item.getItemType() == WorkItemType.APPLICATION)
        .ifPresent(
            item -> {
              item.useDirectBoundary();
              item.setProjectionPosition(message.identifier().hashCode());
              items.save(item);
            });
  }

  private void requireMatchingType(
      WorkListItemReadModel item, WorkItemType eventType, UUID workItemId) {
    if (item.getItemType() != eventType) {
      throw new IllegalStateException("Work item type mismatch for " + workItemId);
    }
  }
}

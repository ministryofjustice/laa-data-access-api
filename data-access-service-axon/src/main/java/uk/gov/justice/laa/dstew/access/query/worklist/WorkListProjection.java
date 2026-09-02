package uk.gov.justice.laa.dstew.access.query.worklist;

import java.util.Objects;
import java.util.stream.Stream;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationUnassignedFromCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;

/** Replayable projection of active work; it is never consulted to route a command. */
@Component
@Namespace("work-list-projection")
public class WorkListProjection {
  private final WorkListItemReadRepository items;
  private final ApplicationDataStore applicationDataStore;

  public WorkListProjection(
      WorkListItemReadRepository items, ApplicationDataStore applicationDataStore) {
    this.items = items;
    this.applicationDataStore = applicationDataStore;
  }

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
                    .and(Sort.by(Sort.Direction.ASC, "id.itemType"))
                    .and(Sort.by(Sort.Direction.ASC, "id.itemId"))));
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
            event.applicationId(),
            null,
            event.occurredAt(),
            event.applicationVersion(),
            message.identifier().hashCode());
    item.setLaaReference(data.laaReference());
    item.setUsedDelegatedFunctions(data.usedDelegatedFunctions());
    item.setCategoryOfLaw(data.categoryOfLaw());
    item.setMatterTypes(
        (data.proceedings() == null ? Stream.<Proceeding>empty() : data.proceedings().stream())
            .map(proceeding -> proceeding.getMatterType())
            .filter(Objects::nonNull)
            .distinct()
            .toList());
    item.setApplicationStatus("APPLICATION_SUBMITTED");
    items.save(item);
  }

  /** PA creation directly activates one PA work item under its parent application. */
  @EventHandler
  public void on(PriorAuthorityCreatedEvent event, EventMessage message) {
    items.save(
        new WorkListItemReadModel(
            WorkItemType.PRIOR_AUTHORITY,
            event.submissionId(),
            event.applicationId(),
            event.applicationId(),
            event.occurredAt(),
            event.dataVersion(),
            message.identifier().hashCode()));
  }

  /** A terminal application decision removes only its application work row. */
  @EventHandler
  public void on(ApplicationDecisionMadeEvent event) {
    items.deleteByIdItemTypeAndIdItemId(WorkItemType.APPLICATION, event.applicationId());
  }

  /** Mirrors legacy direct assignment into the new projection during migration. */
  @EventHandler
  public void on(ApplicationAssignedToCaseworkerEvent event, EventMessage message) {
    items
        .findById(new WorkListItemId(WorkItemType.APPLICATION, event.applicationId()))
        .ifPresent(
            item -> {
              item.setAssigneeId(event.caseworkerId());
              item.setItemVersion(event.applicationVersion());
              item.setUpdatedAt(event.occurredAt());
              item.setProjectionPosition(message.identifier().hashCode());
              items.save(item);
            });
  }

  /** Mirrors legacy direct unassignment into the new projection during migration. */
  @EventHandler
  public void on(ApplicationUnassignedFromCaseworkerEvent event, EventMessage message) {
    items
        .findById(new WorkListItemId(WorkItemType.APPLICATION, event.applicationId()))
        .ifPresent(
            item -> {
              item.setAssigneeId(null);
              item.setItemVersion(event.applicationVersion());
              item.setUpdatedAt(event.occurredAt());
              item.setProjectionPosition(message.identifier().hashCode());
              items.save(item);
            });
  }

  /** Applies a generic direct assignment to the event's immutable work-item identity. */
  @EventHandler
  public void on(WorkItemAssigned event, EventMessage message) {
    items
        .findById(new WorkListItemId(event.workItemId().type(), event.workItemId().id()))
        .ifPresent(
            item -> {
              item.setAssigneeId(event.caseworkerId());
              item.setItemVersion(event.itemVersion());
              item.setAssignmentVersion(event.assignmentVersion());
              item.setUpdatedAt(event.occurredAt());
              item.setProjectionPosition(message.identifier().hashCode());
              items.save(item);
            });
  }

  /** Applies a generic direct unassignment to the event's immutable work-item identity. */
  @EventHandler
  public void on(WorkItemUnassigned event, EventMessage message) {
    items
        .findById(new WorkListItemId(event.workItemId().type(), event.workItemId().id()))
        .ifPresent(
            item -> {
              item.setAssigneeId(null);
              item.setItemVersion(event.itemVersion());
              item.setAssignmentVersion(event.assignmentVersion());
              item.setUpdatedAt(event.occurredAt());
              item.setProjectionPosition(message.identifier().hashCode());
              items.save(item);
            });
  }

  /** Deletes all disposable rows before event-stream replay. */
  @ResetHandler
  public void reset() {
    items.deleteAllInBatch();
  }
}

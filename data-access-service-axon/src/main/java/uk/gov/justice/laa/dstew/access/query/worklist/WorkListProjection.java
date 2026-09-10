package uk.gov.justice.laa.dstew.access.query.worklist;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
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
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;

/** Replayable projection of active work; it is never consulted to route a command. */
@Component
@Namespace("work-list-projection")
@Slf4j
public class WorkListProjection {
  private final WorkListItemReadRepository items;
  private final ApplicationDataStore applicationDataStore;
  private final PriorAuthorityDataStore priorAuthorityDataStore;

  /** Creates a projection backed by its read repository and event data stores. */
  public WorkListProjection(
      WorkListItemReadRepository items,
      ApplicationDataStore applicationDataStore,
      PriorAuthorityDataStore priorAuthorityDataStore) {
    this.items = items;
    this.applicationDataStore = applicationDataStore;
    this.priorAuthorityDataStore = priorAuthorityDataStore;
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
    items.save(item);
  }

  /** PA submission directly activates one PA work item under its parent application. */
  @EventHandler
  public void on(PriorAuthoritySubmittedEvent event, EventMessage message) {
    ApplicationDataPayload parentData = applicationDataStore.getLatest(event.applicationId());
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

  /** Deletes all disposable rows before event-stream replay. */
  @ResetHandler
  public void reset() {
    items.deleteAllInBatch();
  }

  private void requireMatchingType(
      WorkListItemReadModel item, WorkItemType eventType, UUID workItemId) {
    if (item.getItemType() != eventType) {
      throw new IllegalStateException("Work item type mismatch for " + workItemId);
    }
  }

  private void populateApplicationFields(WorkListItemReadModel item, ApplicationDataPayload data) {
    item.setLaaReference(data.laaReference());
    item.setCategoryOfLaw(data.categoryOfLaw());
    item.setUsedDelegatedFunctions(data.usedDelegatedFunctions());
    item.setMatterTypes(
        (data.proceedings() == null ? Stream.<Proceeding>empty() : data.proceedings().stream())
            .map(Proceeding::getMatterType)
            .filter(Objects::nonNull)
            .distinct()
            .toList());
  }
}

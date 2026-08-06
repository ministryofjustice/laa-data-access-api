package uk.gov.justice.laa.dstew.access.query.workqueue;

import java.util.UUID;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.workitem.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.workitem.WorkItemId;
import uk.gov.justice.laa.dstew.access.command.workitem.WorkItemUnassigned;

/** Independently replayable projection maintaining the work queue of undecided applications. */
@Component
@Namespace("work-queue-projection")
public class WorkQueueProjection {

  private final WorkQueueReadRepository repository;
  private final ApplicationDataStore applicationDataStore;

  public WorkQueueProjection(
      WorkQueueReadRepository repository, ApplicationDataStore applicationDataStore) {
    this.repository = repository;
    this.applicationDataStore = applicationDataStore;
  }

  /** Inserts a work queue row when an application is created. */
  @EventHandler
  public void on(ApplicationCreatedEvent event) {
    ApplicationDataPayload data =
        applicationDataStore.get(event.applicationId(), event.applicationDataVersion());
    repository.save(
        WorkQueueReadModel.builder()
            .itemId(event.applicationId())
            .itemType(WorkQueueItemType.APPLICATION)
            .assignedTo(null)
            .laaReference(data == null ? null : data.laaReference())
            .submittedAt(data == null ? null : data.submittedAt())
            .build());
  }

  /** Updates the assigned caseworker when its WorkItem is assigned. */
  @EventHandler
  public void on(WorkItemAssigned event) {
    UUID applicationId = WorkItemId.toItemId(event.workItemId());
    repository
        .findById(applicationId)
        .ifPresent(
            item -> {
              item.setAssignedTo(event.caseworkerId());
              repository.save(item);
            });
  }

  /** Clears the assigned caseworker when its WorkItem is unassigned. */
  @EventHandler
  public void on(WorkItemUnassigned event) {
    UUID applicationId = WorkItemId.toItemId(event.workItemId());
    repository
        .findById(applicationId)
        .ifPresent(
            item -> {
              item.setAssignedTo(null);
              repository.save(item);
            });
  }

  /** Removes the work queue row when a decision is made on an application. */
  @EventHandler
  public void on(ApplicationDecisionMadeEvent event, QueryUpdateEmitter queryUpdateEmitter) {
    repository.deleteById(event.applicationId());
    queryUpdateEmitter.emit(
        AwaitWorkQueueRemovalQuery.class,
        query -> query.applicationId().equals(event.applicationId()),
        true);
  }

  /** Supplies the initial state for a subscription waiting for a work queue row removal. */
  @QueryHandler
  public boolean handle(AwaitWorkQueueRemovalQuery query) {
    return !repository.existsById(query.applicationId());
  }

  /** Clears the work queue projection before replay. */
  @ResetHandler
  public void reset() {
    repository.deleteAllInBatch();
  }

  /** Returns a paginated work queue filtered by assignment state. */
  @QueryHandler
  public FindWorkQueueItemsResult handle(FindWorkQueueItemsQuery query) {
    int zeroBasedPage = query.page() - 1;
    PageRequest pageable = PageRequest.of(zeroBasedPage, query.pageSize());
    Page<WorkQueueReadModel> page;
    if (Boolean.TRUE.equals(query.unassigned())) {
      page = repository.findByAssignedToIsNullOrderBySubmittedAtAsc(pageable);
    } else if (query.assignedTo() != null) {
      page = repository.findByAssignedToOrderBySubmittedAtAsc(query.assignedTo(), pageable);
    } else {
      page = repository.findAll(pageable);
    }
    return new FindWorkQueueItemsResult(
        page.getContent(), page.getTotalElements(), query.page(), query.pageSize());
  }
}

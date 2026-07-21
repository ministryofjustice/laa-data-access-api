package uk.gov.justice.laa.dstew.access.query.workitem;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.ResetHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationSubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.workallocation.CaseworkerAssignedEvent;
import uk.gov.justice.laa.dstew.access.command.workallocation.CaseworkerUnassignedEvent;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;

/**
 * Subscribing projection of the caseworker workload (the {@code work_items} table).
 *
 * <p>Workload is a pure read model: it adds a row when something is submitted and (later) reflects
 * assignment. There is no WorkItem aggregate — the assign/unassign invariant lives on the owning
 * Application / prior-authority member; this projection only mirrors the outcome.
 *
 * <p>Runs in the same subscribing processing group as the current-state projection, so a work item
 * is queryable synchronously once its submission event is handled.
 */
@Component
@ProcessingGroup("workitem-projection")
public class WorkItemProjection {

  private final WorkItemRepository repository;
  private final ApplicationReadRepository applicationReadRepository;

  /** Wires the work-item store and the application read model used to resolve an LAA reference. */
  public WorkItemProjection(
      WorkItemRepository repository, ApplicationReadRepository applicationReadRepository) {
    this.repository = repository;
    this.applicationReadRepository = applicationReadRepository;
  }

  /** Adds (or refreshes) an application work item when an application is submitted. */
  @EventHandler
  public void on(ApplicationSubmittedEvent event) {
    upsert(
        event.applyApplicationId(),
        WorkType.APPLICATION,
        event.applyApplicationId(),
        null,
        event.laaReference(),
        event.occurredAt());
  }

  /** Adds (or refreshes) a prior-authority work item when a prior authority is submitted. */
  @EventHandler
  public void on(PriorAuthoritySubmittedEvent event) {
    String laaReference =
        applicationReadRepository
            .findById(event.applyApplicationId())
            .map(application -> application.getLaaReference())
            .orElse(null);
    upsert(
        event.priorAuthorityId(),
        WorkType.PRIOR_AUTHORITY,
        event.applyApplicationId(),
        event.priorAuthorityId(),
        laaReference,
        event.occurredAt());
  }

  /** Reflects an assignment onto the work item. */
  @EventHandler
  public void on(CaseworkerAssignedEvent event) {
    updateAssignee(event.workItemId(), event.caseworkerId(), event.occurredAt());
  }

  /** Clears the assignment on the work item. */
  @EventHandler
  public void on(CaseworkerUnassignedEvent event) {
    updateAssignee(event.workItemId(), null, event.occurredAt());
  }

  /** Returns a filtered, paginated page of work items. */
  @QueryHandler
  public WorkloadPage handle(GetWorkloadQuery query) {
    PageRequest pageRequest =
        PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<WorkItemRecord> page = repository.findAll(toSpecification(query), pageRequest);
    return new WorkloadPage(
        page.getContent().stream().map(WorkItemView::from).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  /** Clears the disposable work-item table before replay. */
  @ResetHandler
  public void reset() {
    repository.deleteAllInBatch();
  }

  private void upsert(
      UUID workItemId,
      WorkType workType,
      UUID applicationId,
      UUID priorAuthorityId,
      String laaReference,
      Instant occurredAt) {
    WorkItemRecord record =
        repository
            .findById(workItemId)
            .orElseGet(
                () ->
                    WorkItemRecord.builder().workItemId(workItemId).createdAt(occurredAt).build());
    record.setWorkType(workType);
    record.setApplicationId(applicationId);
    record.setPriorAuthorityId(priorAuthorityId);
    record.setLaaReference(laaReference);
    record.setUpdatedAt(occurredAt);
    repository.save(record);
  }

  private void updateAssignee(UUID workItemId, UUID caseworkerId, Instant occurredAt) {
    repository
        .findById(workItemId)
        .ifPresent(
            record -> {
              record.setAssignedCaseworkerId(caseworkerId);
              record.setUpdatedAt(occurredAt);
              repository.save(record);
            });
  }

  private static UUID workItemId(UUID applicationId, UUID priorAuthorityId) {
    return priorAuthorityId != null ? priorAuthorityId : applicationId;
  }

  private Specification<WorkItemRecord> toSpecification(GetWorkloadQuery query) {
    return (root, criteriaQuery, builder) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (query.workType() != null) {
        predicates.add(builder.equal(root.get("workType"), query.workType()));
      }
      if (query.assignedCaseworkerId() != null) {
        predicates.add(
            builder.equal(root.get("assignedCaseworkerId"), query.assignedCaseworkerId()));
      } else if (query.unassigned()) {
        predicates.add(builder.isNull(root.get("assignedCaseworkerId")));
      }
      return builder.and(predicates.toArray(new Predicate[0]));
    };
  }
}

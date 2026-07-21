package uk.gov.justice.laa.dstew.access.command.workallocation;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.spring.stereotype.Aggregate;
import uk.gov.justice.laa.dstew.access.exception.ConflictException;

/**
 * Event-sourced aggregate owning the caseworker assignment of a single work item.
 *
 * <p>This is the "extract the allocation" alternative to holding assignment inside the {@code
 * Application} aggregate. Each work item's assignment lives on its <b>own stream</b>, keyed by
 * {@code workItemId} (an application id or a prior authority id). The one-active-caseworker
 * invariant is a single-identity concern, so it is enforced by ordinary optimistic concurrency on
 * this stream — no cross-aggregate coordination, no aggregate-member event routing, and two work
 * items on the same application allocate concurrently on independent streams.
 *
 * <p>The aggregate is created lazily on first assignment; whether the work item exists (and is
 * allocatable) is a separate, non-transactional precondition checked against the {@code work_items}
 * read model by the application layer before a command is dispatched.
 */
@Aggregate
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class WorkAllocation {

  @AggregateIdentifier private UUID allocationId;
  private UUID workItemId;
  private UUID assignedCaseworkerId;

  /**
   * Assigns a caseworker to this work item. Creates the allocation stream on first assignment.
   * Idempotent when the same caseworker is assigned again; rejected with a conflict when a
   * different caseworker already holds it.
   */
  @CommandHandler
  @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
  void handle(AssignCaseworkerCommand command, Clock clock) {
    if (assignedCaseworkerId != null) {
      if (assignedCaseworkerId.equals(command.caseworkerId())) {
        return;
      }
      throw new ConflictException(
          "Work item " + command.workItemId() + " is already assigned to a different caseworker");
    }
    apply(
        new CaseworkerAssignedEvent(
            command.allocationId(),
            command.workItemId(),
            command.caseworkerId(),
            Instant.now(clock)));
  }

  /** Clears this work item's caseworker assignment. Rejected with a conflict when not assigned. */
  @CommandHandler
  void handle(UnassignCaseworkerCommand command, Clock clock) {
    if (assignedCaseworkerId == null) {
      throw new ConflictException(
          "Work item " + command.workItemId() + " is not assigned to a caseworker");
    }
    apply(
        new CaseworkerUnassignedEvent(
            command.allocationId(), command.workItemId(), Instant.now(clock)));
  }

  @EventSourcingHandler
  void on(CaseworkerAssignedEvent event) {
    allocationId = event.allocationId();
    workItemId = event.workItemId();
    assignedCaseworkerId = event.caseworkerId();
  }

  @EventSourcingHandler
  void on(CaseworkerUnassignedEvent event) {
    assignedCaseworkerId = null;
  }

  protected WorkAllocation() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}

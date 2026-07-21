package uk.gov.justice.laa.dstew.access.command.workallocation;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to assign a caseworker to a work item. Targets the {@link WorkAllocation} aggregate by
 * its namespaced {@code allocationId} (see {@link AllocationId}); {@code workItemId} is carried
 * through so downstream events and read models can refer to the work item's natural id.
 */
public record AssignCaseworkerCommand(
    @TargetAggregateIdentifier UUID allocationId, UUID workItemId, UUID caseworkerId) {}

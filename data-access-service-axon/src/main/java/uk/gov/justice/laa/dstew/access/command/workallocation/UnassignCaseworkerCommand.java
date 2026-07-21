package uk.gov.justice.laa.dstew.access.command.workallocation;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to clear the caseworker assignment on a work item. Targets the {@link WorkAllocation}
 * aggregate by its namespaced {@code allocationId} (see {@link AllocationId}).
 */
public record UnassignCaseworkerCommand(
    @TargetAggregateIdentifier UUID allocationId, UUID workItemId) {}

package uk.gov.justice.laa.dstew.access.command.application;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to clear the caseworker assignment on a prior authority work item. Routed to the owning
 * prior authority member by {@code priorAuthorityId} on the {@link ApplicationAggregate} stream.
 */
public record UnassignPriorAuthorityCaseworkerCommand(
    @TargetAggregateIdentifier UUID applyApplicationId, UUID priorAuthorityId) {}

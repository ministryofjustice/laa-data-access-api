package uk.gov.justice.laa.dstew.access.command.application;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to assign a caseworker to a prior authority work item. Routed to the owning prior
 * authority member by {@code priorAuthorityId} on the {@link ApplicationAggregate} stream.
 */
public record AssignPriorAuthorityCaseworkerCommand(
    @TargetAggregateIdentifier UUID applyApplicationId, UUID priorAuthorityId, UUID caseworkerId) {}

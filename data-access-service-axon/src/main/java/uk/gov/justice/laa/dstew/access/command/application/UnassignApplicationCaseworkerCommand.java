package uk.gov.justice.laa.dstew.access.command.application;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to clear the caseworker assignment on an application work item. Routed to the owning
 * {@link ApplicationAggregate} by {@code applyApplicationId}.
 */
public record UnassignApplicationCaseworkerCommand(
    @TargetAggregateIdentifier UUID applyApplicationId) {}

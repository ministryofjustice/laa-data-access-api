package uk.gov.justice.laa.dstew.access.command.application;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to assign a caseworker to an application work item. Routed to the owning {@link
 * ApplicationAggregate} by {@code applyApplicationId}. Carries no personal data.
 */
public record AssignApplicationCaseworkerCommand(
    @TargetAggregateIdentifier UUID applyApplicationId, UUID caseworkerId) {}

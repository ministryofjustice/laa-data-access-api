package uk.gov.justice.laa.dstew.access.command.application;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to submit an existing prior authority draft, transitioning it from {@code DRAFT} to
 * {@code SUBMITTED}. Routed to the owning prior authority member by {@code priorAuthorityId}.
 */
public record SubmitPriorAuthorityCommand(
    @TargetAggregateIdentifier UUID applyApplicationId, UUID priorAuthorityId) {}

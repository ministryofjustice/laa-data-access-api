package uk.gov.justice.laa.dstew.access.command.application;

import java.util.Map;
import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to update an existing prior authority draft in place. Routed to the owning prior
 * authority member by {@code priorAuthorityId}; only permitted while the member is still in {@code
 * DRAFT}.
 */
public record UpdatePriorAuthorityDraftCommand(
    @TargetAggregateIdentifier UUID applyApplicationId,
    UUID priorAuthorityId,
    Map<String, Object> content) {}

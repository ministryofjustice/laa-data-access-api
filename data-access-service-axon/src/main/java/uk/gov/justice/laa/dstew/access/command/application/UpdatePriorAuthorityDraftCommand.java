package uk.gov.justice.laa.dstew.access.command.application;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to update an existing prior authority draft. Carries no personal data: the latest body is
 * overwritten by the application layer in the deletable {@code prior_authority_drafts} table.
 * Routed to the owning member by {@code priorAuthorityId}; only permitted while it is still in
 * {@code DRAFT}.
 */
public record UpdatePriorAuthorityDraftCommand(
    @TargetAggregateIdentifier UUID applyApplicationId, UUID priorAuthorityId) {}

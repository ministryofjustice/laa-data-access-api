package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Pointer event recording that a prior authority draft was updated in place. Carries no personal
 * data: the latest draft body is overwritten in the deletable {@code prior_authority_drafts} table,
 * keyed by {@code priorAuthorityId}.
 */
public record PriorAuthorityDraftUpdatedEvent(
    UUID applyApplicationId, UUID priorAuthorityId, Instant occurredAt) {}

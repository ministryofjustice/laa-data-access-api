package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Pointer event recording that a new prior authority draft was created against a submitted
 * application. Carries no personal data: the mutable draft body lives in the deletable {@code
 * prior_authority_drafts} table, keyed by {@code priorAuthorityId}.
 */
public record PriorAuthorityDraftedEvent(
    UUID applyApplicationId, UUID priorAuthorityId, Instant occurredAt) {}

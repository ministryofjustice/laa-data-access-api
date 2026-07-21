package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Records that an existing application draft was edited. PII-free pointer — the latest draft body
 * lives in the deletable {@code drafts} table, overwritten in place; the event carries no content.
 */
public record ApplicationDraftUpdatedEvent(UUID applyApplicationId, Instant occurredAt) {}

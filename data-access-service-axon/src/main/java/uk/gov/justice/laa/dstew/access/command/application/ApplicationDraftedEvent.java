package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Genesis event for an application: records that it was first drafted. PII-free pointer — the draft
 * body lives in the deletable {@code drafts} table, not in the event. Submission is later modelled
 * as a guarded state transition ({@link ApplicationSubmittedEvent}) on the same aggregate.
 */
public record ApplicationDraftedEvent(UUID applyApplicationId, Instant occurredAt) {}

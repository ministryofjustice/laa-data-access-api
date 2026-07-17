package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Genesis event for an application: records that it was first drafted. Submission is later modelled
 * as a guarded state transition ({@link ApplicationSubmittedEvent}) on the same aggregate.
 */
public record ApplicationDraftedEvent(
    UUID draftApplicationId, Map<String, Object> content, Instant occurredAt) {}

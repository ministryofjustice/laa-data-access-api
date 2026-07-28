package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Event recording that a prior authority draft was submitted, transitioning it from {@code DRAFT}
 * to {@code SUBMITTED}. Carries no personal data.
 */
public record PriorAuthoritySubmittedEvent(
    UUID applyApplicationId, UUID priorAuthorityId, Instant occurredAt) {}

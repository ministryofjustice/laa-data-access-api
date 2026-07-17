package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event recording that a new prior authority draft was created against a submitted application. The
 * {@code content} map is still carried inline; slimming prior authority events to PII-free pointers
 * is a tracked follow-up.
 */
public record PriorAuthorityDraftedEvent(
    UUID applyApplicationId,
    UUID priorAuthorityId,
    Map<String, Object> content,
    Instant occurredAt) {}

package uk.gov.justice.laa.dstew.access.command.synchronousapplication;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Event recording that an existing prior authority draft was updated in place. */
public record DraftPriorAuthorityUpdatedEvent(
    UUID applyApplicationId,
    UUID priorAuthorityId,
    Map<String, Object> content,
    Instant occurredAt) {}

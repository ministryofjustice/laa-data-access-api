package uk.gov.justice.laa.dstew.access.command.synchronousapplication;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Event recording that an existing draft application was updated in place. */
public record DraftApplicationUpdatedEvent(
    UUID draftApplicationId, Map<String, Object> content, Instant occurredAt) {}

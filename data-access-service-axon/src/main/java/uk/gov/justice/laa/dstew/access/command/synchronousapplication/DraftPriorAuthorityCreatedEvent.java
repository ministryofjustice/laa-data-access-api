package uk.gov.justice.laa.dstew.access.command.synchronousapplication;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Event recording that a new prior authority draft was created against an application. */
public record DraftPriorAuthorityCreatedEvent(
    UUID applyApplicationId,
    UUID priorAuthorityId,
    Map<String, Object> content,
    Instant occurredAt) {}

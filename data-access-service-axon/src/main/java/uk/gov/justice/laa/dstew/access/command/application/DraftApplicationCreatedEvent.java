package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Event recording that a new draft application was created. */
public record DraftApplicationCreatedEvent(
    UUID draftApplicationId, Map<String, Object> content, Instant occurredAt) {}

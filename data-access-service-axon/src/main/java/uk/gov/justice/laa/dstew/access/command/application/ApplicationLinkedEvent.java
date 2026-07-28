package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;

/** Records that an Application belongs to a lead Application's linked group. */
public record ApplicationLinkedEvent(
    UUID applicationId, UUID leadApplicationId, String serialisedRequest, Instant occurredAt) {}

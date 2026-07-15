package uk.gov.justice.laa.dstew.access.command.application;

import java.util.UUID;

/** Records the Application that owns an Apply Application identifier. */
public record ApplyApplicationIdClaimedEvent(
    UUID applyApplicationId,
    UUID applicationId,
    ApplicationCreatedEvent applicationCreatedEvent,
    UUID leadApplicationId) {}

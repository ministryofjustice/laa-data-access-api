package uk.gov.justice.laa.dstew.access.command.application;

import java.util.UUID;

/** Records that a failed Application creation no longer owns its Apply identifier. */
public record ApplyApplicationIdReleasedEvent(UUID applyApplicationId, UUID applicationId) {}

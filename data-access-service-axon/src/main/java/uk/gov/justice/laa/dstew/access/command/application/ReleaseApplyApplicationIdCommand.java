package uk.gov.justice.laa.dstew.access.command.application;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Releases a claim when its corresponding Application cannot be created. */
public record ReleaseApplyApplicationIdCommand(
    @TargetAggregateIdentifier UUID applyApplicationId, UUID applicationId) {}

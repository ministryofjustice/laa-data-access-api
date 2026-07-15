package uk.gov.justice.laa.dstew.access.command.application;

import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Creates the Application aggregate after its Apply identifier has been claimed. */
public record FinaliseApplicationCreationCommand(
    @TargetAggregateIdentifier UUID applicationId,
    ApplicationCreatedEvent applicationCreatedEvent,
    UUID leadApplicationId) {}

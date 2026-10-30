package uk.gov.justice.laa.dstew.access.command.multiaggregate;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Test command for appending events to two application aggregates. */
@Command(routingKey = "firstApplicationId")
public record AppendToTwoApplicationsCommand(
    @TargetEntityId UUID firstApplicationId,
    UUID secondApplicationId,
    UUID caseworkerId,
    Instant occurredAt) {}

package uk.gov.justice.laa.dstew.access.command.application.assignment;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Assigns a caseworker to one Application aggregate. */
public record AssignCaseworkerToApplicationCommand(
    @TargetAggregateIdentifier UUID applicationId,
    UUID caseworkerId,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {}

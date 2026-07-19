package uk.gov.justice.laa.dstew.access.command.application.assignment;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Unassigns the current caseworker from one Application aggregate. */
public record UnassignCaseworkerFromApplicationCommand(
    @TargetAggregateIdentifier UUID applicationId,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {}

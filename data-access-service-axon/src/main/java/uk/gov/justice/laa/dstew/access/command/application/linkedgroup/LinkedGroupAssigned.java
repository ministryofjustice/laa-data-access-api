package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Thin immutable record of one shared assignment across active linked members. */
@Event
public record LinkedGroupAssigned(
    @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    List<UUID> affectedApplicationIds,
    long membershipVersion,
    long assignmentVersion,
    UUID caseworkerId,
    Instant occurredAt) {}


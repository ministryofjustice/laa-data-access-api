package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Thin immutable record returning all active linked members to the open work list. */
@Event
public record LinkedGroupUnassigned(
    @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    List<UUID> affectedApplicationIds,
    long membershipVersion,
    long assignmentVersion,
    Instant occurredAt) {}


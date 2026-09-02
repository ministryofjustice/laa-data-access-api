package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Replayable change to one linked member's work-list eligibility. */
@Event
public record LinkedGroupMemberWorkItemChanged(
    @EventTag(key = "LinkedApplicationGroupAggregate") UUID groupId,
    UUID applicationId,
    boolean active,
    long membershipVersion,
    Instant occurredAt) {}

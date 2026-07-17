package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when an application is added to an already-existing {@link
 * LinkedApplicationGroupAggregate}.
 */
public record MemberAddedToGroupEvent(UUID groupId, UUID memberId, Instant occurredAt) {}

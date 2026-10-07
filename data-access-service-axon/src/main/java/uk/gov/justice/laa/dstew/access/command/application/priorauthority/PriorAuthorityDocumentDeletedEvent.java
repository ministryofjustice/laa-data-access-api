package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/** Persisted event emitted when a prior-authority draft document is marked deleted. */
@Event
public record PriorAuthorityDocumentDeletedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    UUID documentId,
    Instant deletedAt,
    UUID parentApplicationId) {}

package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;

/**
 * Thin-pointer event emitted when a Prior Authority draft is submitted. Contains no personal data —
 * PII remains in the prior_authority_data table at the referenced dataVersion.
 */
@Event
public record PriorAuthoritySubmittedEvent(
    @EventTag(key = "PriorAuthorityAggregate") UUID submissionId,
    UUID applicationId,
    long dataVersion,
    String status,
    Instant occurredAt) {}

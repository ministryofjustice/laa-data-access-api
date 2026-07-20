package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Command to permanently remove all persisted PII versions for an application. */
public record RedactApplicationPiiCommand(
    @TargetAggregateIdentifier UUID applicationId,
    long expectedApplicationVersion,
    String reason,
    String actor,
    Instant occurredAt) {}

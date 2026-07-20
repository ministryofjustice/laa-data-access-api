package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;

/** Emitted when all PII for an application has been irreversibly deleted. */
public record ApplicationPiiRedactedEvent(
    UUID applicationId, long applicationVersion, String reason, String actor, Instant occurredAt) {}

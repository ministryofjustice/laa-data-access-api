package uk.gov.justice.laa.dstew.access.command.application.assignment;

import java.time.Instant;
import java.util.UUID;

/** Thin event recording a caseworker assignment and its referenced sensitive-data version. */
public record ApplicationAssignedToCaseworkerEvent(
    UUID applicationId,
    long applicationVersion,
    long applicationDataVersion,
    UUID caseworkerId,
    Instant occurredAt) {}

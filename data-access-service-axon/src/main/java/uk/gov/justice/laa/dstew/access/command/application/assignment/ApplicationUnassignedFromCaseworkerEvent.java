package uk.gov.justice.laa.dstew.access.command.application.assignment;

import java.time.Instant;
import java.util.UUID;

/** Thin event recording removal of an application's caseworker assignment. */
public record ApplicationUnassignedFromCaseworkerEvent(
    UUID applicationId, long applicationVersion, long applicationDataVersion, Instant occurredAt) {}

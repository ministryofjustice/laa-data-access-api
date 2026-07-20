package uk.gov.justice.laa.dstew.access.command.application.note;

import java.time.Instant;
import java.util.UUID;

/** Records that a note was appended to an Application's immutable data. */
public record NoteCreatedEvent(
    UUID applicationId, long applicationDataVersion, Instant occurredAt) {}

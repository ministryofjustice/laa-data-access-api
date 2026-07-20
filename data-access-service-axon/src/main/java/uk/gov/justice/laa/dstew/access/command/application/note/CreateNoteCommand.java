package uk.gov.justice.laa.dstew.access.command.application.note;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Routes a note-creation request to the correct Application aggregate stream. */
public record CreateNoteCommand(
    @TargetAggregateIdentifier UUID applicationId,
    String noteText,
    String serialisedNoteRequest,
    Instant occurredAt) {}

package uk.gov.justice.laa.dstew.access.command.application.note;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Routes a note-creation request to the correct Application aggregate stream. */
@Command(routingKey = "applicationId")
public record CreateNoteCommand(
    @TargetEntityId UUID applicationId,
    String noteText,
    String serialisedNoteRequest,
    Instant occurredAt) {}

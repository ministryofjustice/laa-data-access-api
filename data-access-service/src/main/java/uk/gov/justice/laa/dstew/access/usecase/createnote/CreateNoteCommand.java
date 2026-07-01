package uk.gov.justice.laa.dstew.access.usecase.createnote;

import java.util.UUID;
import lombok.Builder;

/** Command record carrying all fields required to create a note. */
@Builder(toBuilder = true)
public record CreateNoteCommand(
    UUID applicationId, String noteText, String serialisedNoteRequest) {}

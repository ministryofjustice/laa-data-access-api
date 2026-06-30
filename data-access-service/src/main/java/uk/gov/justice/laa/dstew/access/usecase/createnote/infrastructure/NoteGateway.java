package uk.gov.justice.laa.dstew.access.usecase.createnote.infrastructure;

import java.util.UUID;

/** Gateway interface for note persistence in the createNote use case. */
public interface NoteGateway {

  /**
   * Saves a note associated with the given application.
   *
   * @param applicationId the UUID of the application
   * @param notes the note text
   */
  void saveNote(UUID applicationId, String notes);
}

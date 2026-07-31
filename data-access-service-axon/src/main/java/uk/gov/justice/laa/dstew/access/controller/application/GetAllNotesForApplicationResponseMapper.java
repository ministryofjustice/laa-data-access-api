package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationNote;
import uk.gov.justice.laa.dstew.access.model.ApplicationNoteResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationNotesResponse;

/** Maps a list of {@link ApplicationNote} domain objects to an {@link ApplicationNotesResponse}. */
@Component
public class GetAllNotesForApplicationResponseMapper {

  /**
   * Converts a list of application notes to an API response.
   *
   * @param notes list of notes from the application data payload
   * @return application notes response
   */
  public ApplicationNotesResponse toResponse(List<ApplicationNote> notes) {
    List<ApplicationNoteResponse> noteResponses =
        notes.stream().map(this::toApplicationNoteResponse).toList();
    return new ApplicationNotesResponse().notes(noteResponses);
  }

  private ApplicationNoteResponse toApplicationNoteResponse(ApplicationNote note) {
    return new ApplicationNoteResponse()
        .note(note.noteText())
        .createdAt(note.createdAt() != null ? note.createdAt().atOffset(ZoneOffset.UTC) : null);
  }
}

package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.ZoneOffset;
import java.util.List;
import uk.gov.justice.laa.dstew.access.domain.NoteReadModel;
import uk.gov.justice.laa.dstew.access.model.ApplicationNoteResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationNotesResponse;

/** Maps get-all-notes-for-application read models to API responses. */
public class GetAllNotesForApplicationResponseMapper {

  /**
   * Converts a list of note read models to an application notes response.
   *
   * @param notes list of note read models
   * @return application notes response
   */
  public ApplicationNotesResponse toResponse(List<NoteReadModel> notes) {
    List<ApplicationNoteResponse> noteResponses =
        notes.stream().map(this::toApplicationNoteResponse).toList();
    return new ApplicationNotesResponse().notes(noteResponses);
  }

  private ApplicationNoteResponse toApplicationNoteResponse(NoteReadModel note) {
    return new ApplicationNoteResponse()
        .note(note.notes())
        .createdAt(note.createdAt() != null ? note.createdAt().atOffset(ZoneOffset.UTC) : null)
        .createdBy(note.createdBy());
  }
}

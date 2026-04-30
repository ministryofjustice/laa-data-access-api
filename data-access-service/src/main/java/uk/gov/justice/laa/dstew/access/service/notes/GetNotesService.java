package uk.gov.justice.laa.dstew.access.service.notes;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.mapper.NoteMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationNoteResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationNotesResponse;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.NoteRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.utils.ApplicationServiceHelper;

/** Get application notes service. */
@RequiredArgsConstructor
@Service
public class GetNotesService {

  private final ApplicationRepository applicationRepository;
  private final NoteRepository noteRepository;
  private final NoteMapper noteMapper;

  /**
   * Retrieve all notes for an application in ascending date order.
   *
   * @param id UUID of the application
   * @return response containing list of notes in ascending createdAt order
   */
  @AllowApiCaseworker
  public ApplicationNotesResponse getApplicationNotes(final UUID id) {
    ApplicationServiceHelper.getExistingApplication(id, applicationRepository);
    List<ApplicationNoteResponse> notes =
        noteRepository.findByApplicationIdOrderByCreatedAtAsc(id).stream()
            .map(noteMapper::toApplicationNoteResponse)
            .toList();
    return new ApplicationNotesResponse().notes(notes);
  }
}

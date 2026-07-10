package uk.gov.justice.laa.dstew.access.usecase.getallnotesforapplication;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import uk.gov.justice.laa.dstew.access.domain.NoteReadModel;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.usecase.getallnotesforapplication.infrastructure.GetAllNotesForApplicationNoteGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;

/** Orchestrates retrieval of all notes for an application. */
@RequiredArgsConstructor
public class GetAllNotesForApplicationUseCase {

  private final ApplicationGateway applicationGateway;
  private final GetAllNotesForApplicationNoteGateway noteGateway;

  /**
   * Retrieves all notes for an application by id, ordered by creation time ascending.
   *
   * @param applicationId application id
   * @return list of note read models
   * @throws ResourceNotFoundException if no application exists with the given id
   */
  @AllowApiCaseworker
  public List<NoteReadModel> execute(UUID applicationId) {
    if (!applicationGateway.applicationExists(applicationId)) {
      throw new ResourceNotFoundException(
          String.format("No application found with id: %s", applicationId));
    }
    return noteGateway.findByApplicationIdOrderByCreatedAtAsc(applicationId);
  }
}

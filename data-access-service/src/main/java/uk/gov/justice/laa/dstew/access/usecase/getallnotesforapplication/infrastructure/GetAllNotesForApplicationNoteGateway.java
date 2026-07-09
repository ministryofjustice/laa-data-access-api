package uk.gov.justice.laa.dstew.access.usecase.getallnotesforapplication.infrastructure;

import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.NoteReadModel;

/** Gateway for fetching notes for an application. */
public interface GetAllNotesForApplicationNoteGateway {

  /**
   * Returns all notes for the given application in ascending creation order.
   *
   * @param applicationId application id
   * @return list of note read models ordered by creation time ascending
   */
  List<NoteReadModel> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}

package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallnotesforapplication;

import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.NoteReadModel;
import uk.gov.justice.laa.dstew.access.repository.NoteRepository;
import uk.gov.justice.laa.dstew.access.usecase.getallnotesforapplication.infrastructure.GetAllNotesForApplicationNoteGateway;

/** JPA gateway for fetching notes for the get-all-notes-for-application use case. */
public class GetAllNotesForApplicationNoteJpaGateway
    implements GetAllNotesForApplicationNoteGateway {

  private final NoteRepository noteRepository;
  private final GetAllNotesForApplicationGatewayMapper mapper;

  /**
   * Constructs the gateway.
   *
   * @param noteRepository note repository
   * @param mapper gateway mapper
   */
  public GetAllNotesForApplicationNoteJpaGateway(
      NoteRepository noteRepository, GetAllNotesForApplicationGatewayMapper mapper) {
    this.noteRepository = noteRepository;
    this.mapper = mapper;
  }

  @Override
  public List<NoteReadModel> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId) {
    return noteRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId).stream()
        .map(mapper::toNoteReadModel)
        .toList();
  }
}

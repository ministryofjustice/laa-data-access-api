package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallnotesforapplication;

import uk.gov.justice.laa.dstew.access.domain.NoteReadModel;
import uk.gov.justice.laa.dstew.access.entity.NoteEntity;

/** Maps NoteEntity to NoteReadModel. */
public class GetAllNotesForApplicationGatewayMapper {

  /**
   * Maps a note entity to a note read model.
   *
   * @param entity note entity
   * @return note read model
   */
  public NoteReadModel toNoteReadModel(NoteEntity entity) {
    return NoteReadModel.builder()
        .applicationId(entity.getApplicationId())
        .notes(entity.getNotes())
        .createdAt(entity.getCreatedAt())
        .createdBy(entity.getCreatedBy())
        .build();
  }
}

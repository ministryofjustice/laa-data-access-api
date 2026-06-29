package uk.gov.justice.laa.dstew.access.infrastructure.jpa.createnote;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.entity.NoteEntity;
import uk.gov.justice.laa.dstew.access.repository.NoteRepository;
import uk.gov.justice.laa.dstew.access.usecase.createnote.infrastructure.CreateNoteNoteGateway;

/** JPA implementation of {@link CreateNoteNoteGateway}. */
public class CreateNoteNoteJpaGateway implements CreateNoteNoteGateway {

  private final NoteRepository noteRepository;

  /**
   * Constructs the gateway.
   *
   * @param noteRepository the Spring Data repository for notes
   */
  public CreateNoteNoteJpaGateway(NoteRepository noteRepository) {
    this.noteRepository = noteRepository;
  }

  @Override
  public void saveNote(UUID applicationId, String notes) {
    noteRepository.save(NoteEntity.builder().applicationId(applicationId).notes(notes).build());
  }
}

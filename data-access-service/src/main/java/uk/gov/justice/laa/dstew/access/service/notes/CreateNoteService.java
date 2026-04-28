package uk.gov.justice.laa.dstew.access.service.notes;

import jakarta.transaction.Transactional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.NoteEntity;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;
import uk.gov.justice.laa.dstew.access.repository.NoteRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.DomainEventService;
import uk.gov.justice.laa.dstew.access.service.common.ServiceUtilities;

/** Create application notes service. */
@RequiredArgsConstructor
@Service
public class CreateNoteService {

  private final ServiceUtilities serviceUtilities;
  private final NoteRepository noteRepository;
  private final DomainEventService domainEventService;

  /**
   * Create a note for an application.
   *
   * @param id UUID of the application
   * @param request note to be created
   */
  @AllowApiCaseworker
  @Transactional
  public void createApplicationNote(final UUID id, final CreateNoteRequest request) {
    ApplicationEntity application = serviceUtilities.checkIfApplicationExists(id);
    noteRepository.save(NoteEntity.builder().applicationId(id).notes(request.getNotes()).build());
    domainEventService.saveCreateApplicationNoteDomainEvent(application, request);
  }
}

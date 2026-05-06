package uk.gov.justice.laa.dstew.access.service.applications;

import jakarta.transaction.Transactional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.NoteEntity;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.NoteRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.utils.ApplicationServiceHelper;

/** Create application notes service. */
@RequiredArgsConstructor
@Service
public class CreateNoteService {

  private final ApplicationRepository applicationRepository;
  private final NoteRepository noteRepository;
  private final SaveDomainEventService saveDomainEventService;

  /**
   * Create a note for an application.
   *
   * @param id UUID of the application
   * @param request note to be created
   */
  @AllowApiCaseworker
  @Transactional
  public void createApplicationNote(final UUID id, final CreateNoteRequest request) {
    ApplicationEntity application =
        ApplicationServiceHelper.getExistingApplication(id, applicationRepository);
    noteRepository.save(NoteEntity.builder().applicationId(id).notes(request.getNotes()).build());
    saveDomainEventService.saveCreateApplicationNoteDomainEvent(application, request);
  }
}

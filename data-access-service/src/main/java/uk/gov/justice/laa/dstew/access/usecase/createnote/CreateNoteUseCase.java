package uk.gov.justice.laa.dstew.access.usecase.createnote;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.usecase.createnote.infrastructure.CreateNoteNoteGateway;
import uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure.ApplicationGateway;

/** Orchestrates the create-application-note use case. */
@RequiredArgsConstructor
public class CreateNoteUseCase {

  private final ApplicationGateway applicationGateway;
  private final CreateNoteNoteGateway noteGateway;
  private final SaveDomainEventService saveDomainEventService;

  /**
   * Executes the createNote use case.
   *
   * @param command the command carrying all fields from the HTTP request
   * @throws ResourceNotFoundException if no application exists with the given ID
   */
  @AllowApiCaseworker
  @Transactional
  public void execute(CreateNoteCommand command) {
    ApplicationDomain applicationDomain =
        applicationGateway
            .findByApplicationId(command.applicationId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        String.format(
                            "No application found with id: %s", command.applicationId())));

    noteGateway.saveNote(command.applicationId(), command.noteText());

    saveDomainEventService.saveCreateApplicationNoteDomainEvent(
        command.applicationId(), applicationDomain.caseworkerId(), command.serialisedNoteRequest());
  }
}

package uk.gov.justice.laa.dstew.access.controller.application;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.api.ApplicationDraftsApi;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftUseCase;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftUseCase;
import uk.gov.justice.laa.dstew.access.command.application.draft.UpdateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.draft.UpdateApplicationDraftUseCase;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SaveApplicationDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SaveApplicationDraftResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.model.SubmitApplicationDraftResponse;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;

/** HTTP command adapter for Application draft writes. */
@RestController
public class ApplicationDraftCommandController implements ApplicationDraftsApi {

  private final CreateApplicationDraftUseCase createUseCase;
  private final UpdateApplicationDraftUseCase updateUseCase;
  private final SubmitApplicationDraftUseCase submitUseCase;
  private final SaveApplicationDraftCommandMapper saveCommandMapper;
  private final SubmitApplicationDraftCommandMapper submitCommandMapper;

  /** Creates the command adapter. */
  public ApplicationDraftCommandController(
      CreateApplicationDraftUseCase createUseCase,
      UpdateApplicationDraftUseCase updateUseCase,
      SubmitApplicationDraftUseCase submitUseCase,
      SaveApplicationDraftCommandMapper saveCommandMapper,
      SubmitApplicationDraftCommandMapper submitCommandMapper) {
    this.createUseCase = createUseCase;
    this.updateUseCase = updateUseCase;
    this.submitUseCase = submitUseCase;
    this.saveCommandMapper = saveCommandMapper;
    this.submitCommandMapper = submitCommandMapper;
  }

  /** Creates a new Application draft and returns 201 once it is readable. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<SaveApplicationDraftResponse> saveApplicationDraft(
      ServiceName serviceName,
      CreateApplicationDraftRequest createApplicationDraftRequest,
      Integer schemaVersion) {
    CreateApplicationDraftCommand command =
        saveCommandMapper.toCreateCommand(createApplicationDraftRequest, schemaVersion);
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v0/application-drafts/{applicationId}")
            .buildAndExpand(command.applicationId())
            .toUri();
    SaveApplicationDraftResponse response =
        new SaveApplicationDraftResponse(
            command.applicationId(), OffsetDateTime.now(ZoneOffset.UTC));
    boolean projected = createUseCase.execute(command);
    return projected
        ? ResponseEntity.created(location).body(response)
        : ResponseEntity.accepted().location(location).body(response);
  }

  /** Updates an existing Application draft and returns 204. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> updateApplicationDraft(
      ServiceName serviceName,
      UUID applicationId,
      SaveApplicationDraftRequest saveApplicationDraftRequest) {
    UpdateApplicationDraftCommand command =
        saveCommandMapper.toUpdateCommand(applicationId, saveApplicationDraftRequest);
    updateUseCase.execute(command);
    return ResponseEntity.noContent().build();
  }

  /** Submits an in-progress Application draft, promoting it to a fully created Application. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<SubmitApplicationDraftResponse> submitApplicationDraft(
      ServiceName serviceName, UUID applicationId) {
    SubmitApplicationDraftCommand command = submitCommandMapper.toSubmitCommand(applicationId);
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v0/applications/{id}")
            .buildAndExpand(applicationId)
            .toUri();
    SubmitApplicationDraftResponse response =
        new SubmitApplicationDraftResponse(applicationId, OffsetDateTime.now(ZoneOffset.UTC));
    boolean projected = submitUseCase.submit(command);
    return projected
        ? ResponseEntity.ok().location(location).body(response)
        : ResponseEntity.accepted().location(location).body(response);
  }
}

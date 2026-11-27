package uk.gov.justice.laa.dstew.access.controller.application;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.api.ApplicationDraftsV2Api;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftSaveResult;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftV2Command;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftV2UseCase;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftV2Command;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftV2UseCase;
import uk.gov.justice.laa.dstew.access.command.application.draft.UpdateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.draft.UpdateApplicationDraftUseCase;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDraftV2Request;
import uk.gov.justice.laa.dstew.access.model.SaveApplicationDraftV2Response;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.model.SubmitApplicationDraftResponse;
import uk.gov.justice.laa.dstew.access.model.UpdateApplicationDraftV2Request;
import uk.gov.justice.laa.dstew.access.observability.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.observability.logging.aspects.LogMethodResponse;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCallerFactory;

/** HTTP command adapter for v2 Application draft writes. */
@RestController
public class ApplicationDraftV2CommandController implements ApplicationDraftsV2Api {

  private final CreateApplicationDraftV2UseCase createUseCase;
  private final UpdateApplicationDraftUseCase updateUseCase;
  private final SubmitApplicationDraftV2UseCase submitUseCase;
  private final ApplicationDraftV2CommandMapper mapper;
  private final AuthenticatedCallerFactory authenticatedCallerFactory;

  /** Creates the v2 command adapter. */
  public ApplicationDraftV2CommandController(
      CreateApplicationDraftV2UseCase createUseCase,
      UpdateApplicationDraftUseCase updateUseCase,
      SubmitApplicationDraftV2UseCase submitUseCase,
      ApplicationDraftV2CommandMapper mapper,
      AuthenticatedCallerFactory authenticatedCallerFactory) {
    this.createUseCase = createUseCase;
    this.updateUseCase = updateUseCase;
    this.submitUseCase = submitUseCase;
    this.mapper = mapper;
    this.authenticatedCallerFactory = authenticatedCallerFactory;
  }

  /** Creates a v2 Application draft and returns 201 once it is readable, otherwise 202. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<SaveApplicationDraftV2Response> saveApplicationDraftV2(
      ServiceName serviceName,
      CreateApplicationDraftV2Request createApplicationDraftV2Request,
      Integer schemaVersion) {
    CreateApplicationDraftV2Command command =
        mapper.toCreateCommand(createApplicationDraftV2Request, schemaVersion);
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v2/application-drafts/{applicationId}")
            .buildAndExpand(command.applicationId())
            .toUri();
    ApplicationDraftSaveResult result = createUseCase.execute(command);
    SaveApplicationDraftV2Response response =
        new SaveApplicationDraftV2Response(
            command.applicationId(), OffsetDateTime.now(ZoneOffset.UTC), result.draftVersion());
    return result.projected()
        ? ResponseEntity.created(location).body(response)
        : ResponseEntity.accepted().location(location).body(response);
  }

  /** Replaces the body of a v2 Application draft and returns the new draft version. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<SaveApplicationDraftV2Response> updateApplicationDraftV2(
      ServiceName serviceName,
      UUID applicationId,
      UpdateApplicationDraftV2Request updateApplicationDraftV2Request) {
    UpdateApplicationDraftCommand command =
        mapper.toUpdateCommand(
            applicationId,
            updateApplicationDraftV2Request,
            authenticatedCallerFactory.currentCaller());
    long draftVersion = updateUseCase.execute(command);
    return ResponseEntity.ok(
        new SaveApplicationDraftV2Response(
            applicationId, OffsetDateTime.now(ZoneOffset.UTC), draftVersion));
  }

  /** Submits a v2 Application draft, returning 200 once the Application is readable, else 202. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<SubmitApplicationDraftResponse> submitApplicationDraftV2(
      ServiceName serviceName, UUID applicationId) {
    SubmitApplicationDraftV2Command command = mapper.toSubmitCommand(applicationId);
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

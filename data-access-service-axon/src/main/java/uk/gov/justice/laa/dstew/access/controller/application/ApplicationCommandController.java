package uk.gov.justice.laa.dstew.access.controller.application;

import io.swagger.v3.oas.annotations.Hidden;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.api.ApplicationAutoGrantOutcomeCommandApi;
import uk.gov.justice.laa.dstew.access.api.ApplicationCommandApi;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.command.application.assignment.AssignCaseworkerUseCase;
import uk.gov.justice.laa.dstew.access.command.application.assignment.UnassignCaseworkerUseCase;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionUseCase;
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteUseCase;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityUseCase;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCommand;
import uk.gov.justice.laa.dstew.access.command.application.ready.ReadyApplicationResult;
import uk.gov.justice.laa.dstew.access.command.application.ready.RecordAutoGrantOutcomeUseCase;
import uk.gov.justice.laa.dstew.access.command.application.update.UpdateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityRequest;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentDeleteResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUpdateResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;

/** HTTP command adapter for Application writes. */
@RestController
public class ApplicationCommandController
    implements ApplicationCommandApi, ApplicationAutoGrantOutcomeCommandApi {

  private final CreateApplicationUseCase createApplicationUseCase;
  private final MakeApplicationDecisionUseCase makeDecisionUseCase;
  private final CreateNoteUseCase createNoteUseCase;
  private final UnassignCaseworkerUseCase unassignCaseworkerUseCase;
  private final AssignCaseworkerUseCase assignCaseworkerUseCase;
  private final RecordAutoGrantOutcomeUseCase recordAutoGrantOutcomeUseCase;
  private final UpdateApplicationUseCase updateApplicationUseCase;
  private final CreatePriorAuthorityUseCase createPriorAuthorityUseCase;
  private final CreateApplicationCommandMapper commandMapper;
  private final MakeDecisionCommandMapper decisionCommandMapper;
  private final AssignCaseworkerRequestMapper assignCaseworkerRequestMapper;
  private final UnassignCaseworkerRequestMapper unassignCaseworkerRequestMapper;
  private final CreateNoteCommandMapper createNoteCommandMapper;
  private final AutoGrantOutcomeCommandMapper autoGrantOutcomeCommandMapper;
  private final UpdateApplicationCommandMapper updateApplicationCommandMapper;
  private final CreatePriorAuthorityCommandMapper createPriorAuthorityCommandMapper;

  /** Creates the command adapter. */
  public ApplicationCommandController(
      CreateApplicationUseCase createApplicationUseCase,
      MakeApplicationDecisionUseCase makeDecisionUseCase,
      CreateNoteUseCase createNoteUseCase,
      UnassignCaseworkerUseCase unassignCaseworkerUseCase,
      AssignCaseworkerUseCase assignCaseworkerUseCase,
      RecordAutoGrantOutcomeUseCase recordAutoGrantOutcomeUseCase,
      UpdateApplicationUseCase updateApplicationUseCase,
      CreatePriorAuthorityUseCase createPriorAuthorityUseCase,
      CreateApplicationCommandMapper commandMapper,
      MakeDecisionCommandMapper decisionCommandMapper,
      AssignCaseworkerRequestMapper assignCaseworkerRequestMapper,
      UnassignCaseworkerRequestMapper unassignCaseworkerRequestMapper,
      CreateNoteCommandMapper createNoteCommandMapper,
      AutoGrantOutcomeCommandMapper autoGrantOutcomeCommandMapper,
      UpdateApplicationCommandMapper updateApplicationCommandMapper,
      CreatePriorAuthorityCommandMapper createPriorAuthorityCommandMapper) {
    this.createApplicationUseCase = createApplicationUseCase;
    this.makeDecisionUseCase = makeDecisionUseCase;
    this.createNoteUseCase = createNoteUseCase;
    this.unassignCaseworkerUseCase = unassignCaseworkerUseCase;
    this.assignCaseworkerUseCase = assignCaseworkerUseCase;
    this.recordAutoGrantOutcomeUseCase = recordAutoGrantOutcomeUseCase;
    this.updateApplicationUseCase = updateApplicationUseCase;
    this.createPriorAuthorityUseCase = createPriorAuthorityUseCase;
    this.commandMapper = commandMapper;
    this.decisionCommandMapper = decisionCommandMapper;
    this.assignCaseworkerRequestMapper = assignCaseworkerRequestMapper;
    this.unassignCaseworkerRequestMapper = unassignCaseworkerRequestMapper;
    this.createNoteCommandMapper = createNoteCommandMapper;
    this.autoGrantOutcomeCommandMapper = autoGrantOutcomeCommandMapper;
    this.updateApplicationCommandMapper = updateApplicationCommandMapper;
    this.createPriorAuthorityCommandMapper = createPriorAuthorityCommandMapper;
  }

  /** Assigns a caseworker to one or more Applications after validating the complete batch. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> assignCaseworker(
      ServiceName serviceName, CaseworkerAssignRequest request) {
    var assignment = assignCaseworkerRequestMapper.toAssignment(request);
    assignCaseworkerUseCase.assign(
        assignment.caseworkerId(),
        assignment.applicationId(),
        assignment.serialisedRequest(),
        assignment.eventDescription());
    return ResponseEntity.ok().build();
  }

  /** Removes the current caseworker assignment from an Application. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> unassignCaseworker(
      ServiceName serviceName, UUID id, CaseworkerUnassignRequest request) {
    unassignCaseworkerUseCase.execute(unassignCaseworkerRequestMapper.toCommand(id, request));
    return ResponseEntity.ok().build();
  }

  /** Applies an overall and per-proceeding decision to an existing Application version. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> makeDecision(
      ServiceName serviceName, UUID id, MakeDecisionRequest request) {
    makeDecisionUseCase.execute(decisionCommandMapper.toCommand(id, request));
    return ResponseEntity.noContent().build();
  }

  /** Records either terminal outcome of deciding whether an Application can be auto-granted. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> recordAutoGrantOutcome(
      ServiceName serviceName, UUID id, AutoGrantOutcomeRequest request) {
    Object command = autoGrantOutcomeCommandMapper.toCommand(id, request);
    if (command instanceof MarkApplicationReadyCommand readyCommand) {
      ReadyApplicationResult result = recordAutoGrantOutcomeUseCase.recordReady(readyCommand);
      return result == ReadyApplicationResult.RECORDED
          ? ResponseEntity.noContent().build()
          : ResponseEntity.ok().build();
    }
    recordAutoGrantOutcomeUseCase.record(command);
    return ResponseEntity.noContent().build();
  }

  /** Appends a note to an existing Application. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> createApplicationNotes(
      ServiceName serviceName, UUID id, CreateNoteRequest request) {
    createNoteUseCase.execute(createNoteCommandMapper.toCommand(id, request));
    return ResponseEntity.noContent().build();
  }

  /** Dispatches create directly to Axon and returns 201 once the projection is readable. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> createApplication(
      ServiceName serviceName, ApplicationCreateRequest request, Integer schemaVersion) {
    CreateApplicationCommand command = commandMapper.toCommand(request, schemaVersion);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(command.applicationId())
            .toUri();
    createApplicationUseCase.execute(command);
    return ResponseEntity.created(location).build();
  }

  /** Replaces an existing Application's content and optional status. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> updateApplication(
      ServiceName serviceName, UUID id, ApplicationUpdateRequest request) {
    updateApplicationUseCase.execute(updateApplicationCommandMapper.toCommand(id, request));
    return ResponseEntity.noContent().build();
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<CreatePriorAuthorityResponse> createPriorAuthority(
      ServiceName serviceName, UUID id, CreatePriorAuthorityRequest request) {
    CreatePriorAuthorityCommand command = createPriorAuthorityCommandMapper.toCommand(id, request);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{submissionId}")
            .buildAndExpand(command.submissionId())
            .toUri();
    CreatePriorAuthorityResponse body =
        new CreatePriorAuthorityResponse(
            command.submissionId(), command.occurredAt().atOffset(ZoneOffset.UTC));
    boolean projected = createPriorAuthorityUseCase.execute(command);
    return projected
        ? ResponseEntity.created(location).body(body)
        : ResponseEntity.accepted().location(location).body(body);
  }

  @Hidden
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<DocumentUploadResponse> uploadDocument(
      ServiceName serviceName, UUID id, MultipartFile file) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
  }

  @Hidden
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<DocumentUpdateResponse> updateDocument(
      ServiceName serviceName, UUID id, MultipartFile file) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
  }

  @Hidden
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<DocumentDeleteResponse> deleteDocument(
      ServiceName serviceName, UUID id, List<String> documentIds) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
  }
}

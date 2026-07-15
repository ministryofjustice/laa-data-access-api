package uk.gov.justice.laa.dstew.access.controller.application;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.api.ApplicationCommandApi;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;
import uk.gov.justice.laa.dstew.access.model.DocumentDeleteResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUpdateResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.service.applications.SdsService;
import uk.gov.justice.laa.dstew.access.service.applications.UnassignCaseworkerService;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;
import uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.AssignCaseworkerUseCase;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.CreateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.usecase.createnote.CreateNoteUseCase;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.MakeDecisionUseCase;
import uk.gov.justice.laa.dstew.access.usecase.updateapplication.UpdateApplicationUseCase;

/** Controller for handling /api/v0/applications command requests. */
@RequiredArgsConstructor
@RestController
@ExcludeFromGeneratedCodeCoverage
public class ApplicationCommandController implements ApplicationCommandApi {

  private final CreateApplicationUseCase createApplicationUseCase;
  private final CreateApplicationCommandMapper createApplicationCommandMapper;
  private final UpdateApplicationUseCase updateApplicationUseCase;
  private final UpdateApplicationCommandMapper updateApplicationCommandMapper;

  private final AssignCaseworkerUseCase assignCaseworkerUseCase;
  private final AssignCaseworkerCommandMapper assignCaseworkerCommandMapper;
  private final UnassignCaseworkerService unassignCaseworkerService;
  private final MakeDecisionUseCase makeDecisionUseCase;
  private final MakeDecisionCommandMapper makeDecisionCommandMapper;

  private final CreateNoteUseCase createNoteUseCase;
  private final CreateNoteCommandMapper createNoteCommandMapper;
  private final SdsService sdsService;

  @LogMethodArguments
  @LogMethodResponse
  @Override
  public ResponseEntity<Void> createApplication(
      @NotNull ServiceName serviceName,
      @Valid ApplicationCreateRequest applicationCreateReq,
      @Min(1) @RequestHeader(value = "X-Schema-Version", required = false, defaultValue = "1")
          Integer schemaVersion) {
    UUID id =
        createApplicationUseCase
            .execute(
                createApplicationCommandMapper.toCreateCommand(applicationCreateReq, schemaVersion))
            .id();
    URI uri =
        ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    return ResponseEntity.created(uri).build();
  }

  @Override
  @LogMethodResponse
  @LogMethodArguments
  public ResponseEntity<Void> updateApplication(
      @NotNull ServiceName serviceName,
      UUID id,
      @Valid ApplicationUpdateRequest applicationUpdateReq) {
    updateApplicationUseCase.execute(
        updateApplicationCommandMapper.toUpdateApplicationCommand(id, applicationUpdateReq));
    return ResponseEntity.noContent().build();
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> assignCaseworker(
      @NotNull ServiceName serviceName, @Valid CaseworkerAssignRequest request) {
    assignCaseworkerUseCase.execute(
        assignCaseworkerCommandMapper.toAssignCaseworkerCommand(request));
    return ResponseEntity.ok().build();
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> unassignCaseworker(
      @NotNull ServiceName serviceName, UUID id, @Valid CaseworkerUnassignRequest request) {

    unassignCaseworkerService.unassignCaseworker(id, request.getEventHistory());

    return ResponseEntity.ok().build();
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> makeDecision(
      @NotNull ServiceName serviceName, UUID applicationId, @Valid MakeDecisionRequest request) {
    makeDecisionUseCase.execute(
        makeDecisionCommandMapper.toMakeDecisionCommand(applicationId, request));
    return ResponseEntity.noContent().build();
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> createApplicationNotes(
      @NotNull ServiceName serviceName, UUID applicationId, @Valid CreateNoteRequest request) {
    createNoteUseCase.execute(createNoteCommandMapper.toCreateNoteCommand(applicationId, request));
    return ResponseEntity.noContent().build();
  }

  @Hidden
  @LogMethodArguments
  @LogMethodResponse
  @Override
  public ResponseEntity<DocumentUploadResponse> uploadDocument(
      @NotNull ServiceName serviceName, UUID id, MultipartFile file) {
    DocumentUploadResponse response = sdsService.saveFile(id, file);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Hidden
  @LogMethodArguments
  @LogMethodResponse
  @Override
  public ResponseEntity<DocumentUpdateResponse> updateDocument(
      @NotNull ServiceName serviceName, UUID id, MultipartFile file) {
    DocumentUpdateResponse response = sdsService.saveOrUpdateFile(id, file);
    return ResponseEntity.ok(response);
  }

  @Hidden
  @LogMethodArguments
  @LogMethodResponse
  @Override
  public ResponseEntity<DocumentDeleteResponse> deleteDocument(
      ServiceName serviceName, UUID id, List<String> fileKeys) {
    DocumentDeleteResponse response = sdsService.deleteFiles(id, fileKeys);
    return ResponseEntity.ok(response);
  }
}

package uk.gov.justice.laa.dstew.access.controller.application;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.api.PriorAuthorityDraftCommandApi;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityDraftUseCase;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.SubmitPriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.SubmitPriorAuthorityDraftUseCase;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.UpdatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.UpdatePriorAuthorityDraftUseCase;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.model.SubmitPriorAuthorityDraftResponse;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;

/** HTTP command adapter for Prior Authority draft writes. */
@RestController
public class PriorAuthorityDraftCommandController implements PriorAuthorityDraftCommandApi {

  private final CreatePriorAuthorityDraftUseCase createUseCase;
  private final UpdatePriorAuthorityDraftUseCase updateUseCase;
  private final SubmitPriorAuthorityDraftUseCase submitUseCase;
  private final SavePriorAuthorityDraftCommandMapper saveCommandMapper;
  private final SubmitPriorAuthorityDraftCommandMapper submitCommandMapper;

  /** Creates the command adapter. */
  public PriorAuthorityDraftCommandController(
      CreatePriorAuthorityDraftUseCase createUseCase,
      UpdatePriorAuthorityDraftUseCase updateUseCase,
      SubmitPriorAuthorityDraftUseCase submitUseCase,
      SavePriorAuthorityDraftCommandMapper saveCommandMapper,
      SubmitPriorAuthorityDraftCommandMapper submitCommandMapper) {
    this.createUseCase = createUseCase;
    this.updateUseCase = updateUseCase;
    this.submitUseCase = submitUseCase;
    this.saveCommandMapper = saveCommandMapper;
    this.submitCommandMapper = submitCommandMapper;
  }

  /** Creates a new Prior Authority draft and returns 201 once the projection is readable. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<SavePriorAuthorityDraftResponse> savePriorAuthorityDraft(
      ServiceName serviceName, CreatePriorAuthorityDraftRequest createPriorAuthorityDraftRequest) {
    CreatePriorAuthorityDraftCommand command =
        saveCommandMapper.toCreateCommand(createPriorAuthorityDraftRequest);
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v0/prior-authorities/{priorAuthorityId}")
            .buildAndExpand(command.priorAuthorityId())
            .toUri();
    SavePriorAuthorityDraftResponse response =
        new SavePriorAuthorityDraftResponse(command.priorAuthorityId(), OffsetDateTime.now());
    boolean projected = createUseCase.execute(command);
    return projected
        ? ResponseEntity.created(location).body(response)
        : ResponseEntity.accepted().location(location).body(response);
  }

  /** Updates an existing Prior Authority draft and returns 204. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<Void> updatePriorAuthorityDraft(
      ServiceName serviceName,
      UUID priorAuthorityId,
      SavePriorAuthorityDraftRequest savePriorAuthorityDraftRequest) {
    UpdatePriorAuthorityDraftCommand command =
        saveCommandMapper.toUpdateCommand(priorAuthorityId, savePriorAuthorityDraftRequest);
    updateUseCase.execute(command);
    return ResponseEntity.noContent().build();
  }

  /** Submits an in-progress Prior Authority draft, transitioning it to PENDING. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<SubmitPriorAuthorityDraftResponse> submitPriorAuthorityDraft(
      ServiceName serviceName, UUID priorAuthorityId) {
    SubmitPriorAuthorityDraftCommand command =
        submitCommandMapper.toSubmitCommand(priorAuthorityId);
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v0/prior-authorities/{priorAuthorityId}")
            .buildAndExpand(priorAuthorityId)
            .toUri();
    SubmitPriorAuthorityDraftResponse response =
        new SubmitPriorAuthorityDraftResponse(priorAuthorityId, OffsetDateTime.now());
    boolean projected = submitUseCase.submit(command);
    return projected
        ? ResponseEntity.created(location).body(response)
        : ResponseEntity.accepted().location(location).body(response);
  }
}

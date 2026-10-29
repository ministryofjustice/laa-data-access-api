package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.ApplicationAutoGrantOutcomeCommandApi;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCommand;
import uk.gov.justice.laa.dstew.access.command.application.ready.ReadyApplicationResult;
import uk.gov.justice.laa.dstew.access.command.application.ready.RecordAutoGrantOutcomeUseCase;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;

/** HTTP command adapter for Application Auto Grant writes. */
@RestController
public class ApplicationAutoGrantOutcomeController
    implements ApplicationAutoGrantOutcomeCommandApi {

  private final RecordAutoGrantOutcomeUseCase recordAutoGrantOutcomeUseCase;
  private final AutoGrantOutcomeCommandMapper autoGrantOutcomeCommandMapper;

  public ApplicationAutoGrantOutcomeController(
      RecordAutoGrantOutcomeUseCase recordAutoGrantOutcomeUseCase,
      AutoGrantOutcomeCommandMapper autoGrantOutcomeCommandMapper) {
    this.recordAutoGrantOutcomeUseCase = recordAutoGrantOutcomeUseCase;
    this.autoGrantOutcomeCommandMapper = autoGrantOutcomeCommandMapper;
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
}

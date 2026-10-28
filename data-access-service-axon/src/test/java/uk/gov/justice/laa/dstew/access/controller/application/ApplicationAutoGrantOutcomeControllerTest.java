package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCommand;
import uk.gov.justice.laa.dstew.access.command.application.ready.ReadyApplicationResult;
import uk.gov.justice.laa.dstew.access.command.application.ready.RecordAutoGrantOutcomeUseCase;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;

/** Verifies that each controller endpoint delegates to the appropriate use case. */
@ExtendWith(MockitoExtension.class)
public class ApplicationAutoGrantOutcomeControllerTest {

  private AutoGrantOutcomeCommandMapper autoGrantOutcomeCommandMapper;
  private RecordAutoGrantOutcomeUseCase recordAutoGrantOutcomeUseCase;
  private ApplicationAutoGrantOutcomeController controller;

  @BeforeEach
  void setUp() {
    autoGrantOutcomeCommandMapper = mock(AutoGrantOutcomeCommandMapper.class);
    recordAutoGrantOutcomeUseCase = mock(RecordAutoGrantOutcomeUseCase.class);
    controller =
        new ApplicationAutoGrantOutcomeController(
            recordAutoGrantOutcomeUseCase, autoGrantOutcomeCommandMapper);
  }

  @Test
  void givenReadinessRecorded_whenMarkApplicationReady_thenReturnsNoContent() {
    UUID id = UUID.randomUUID();
    ManualOutcomeRequest request = new ManualOutcomeRequest(AutoGrantOutcome.MANUAL);
    MarkApplicationReadyCommand command =
        new MarkApplicationReadyCommand(id, 2L, "{}", Instant.now());
    when(autoGrantOutcomeCommandMapper.toCommand(id, request)).thenReturn(command);
    when(recordAutoGrantOutcomeUseCase.recordReady(command))
        .thenReturn(ReadyApplicationResult.RECORDED);

    assertThat(controller.recordAutoGrantOutcome(null, id, request).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void givenReadinessAlreadyRecorded_whenMarkApplicationReady_thenReturnsOk() {
    UUID id = UUID.randomUUID();
    ManualOutcomeRequest request = new ManualOutcomeRequest(AutoGrantOutcome.MANUAL);
    MarkApplicationReadyCommand command =
        new MarkApplicationReadyCommand(id, 2L, "{}", Instant.now());
    when(autoGrantOutcomeCommandMapper.toCommand(id, request)).thenReturn(command);
    when(recordAutoGrantOutcomeUseCase.recordReady(command))
        .thenReturn(ReadyApplicationResult.ALREADY_RECORDED);

    assertThat(controller.recordAutoGrantOutcome(null, id, request).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }
}

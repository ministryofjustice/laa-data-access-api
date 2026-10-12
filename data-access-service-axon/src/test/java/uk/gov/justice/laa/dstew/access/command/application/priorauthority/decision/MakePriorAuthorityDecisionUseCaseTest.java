package uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

class MakePriorAuthorityDecisionUseCaseTest {

  @Test
  void givenDecisionCommand_whenExecute_thenDispatchesCommand() {
    RetryingCommandDispatcher dispatcher = mock(RetryingCommandDispatcher.class);
    MakePriorAuthorityDecisionUseCase useCase = new MakePriorAuthorityDecisionUseCase(dispatcher);
    UUID priorAuthorityId = UUID.randomUUID();
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            TestJwtDecoderConfig.CASEWORKER_ID,
            0L,
            "GRANTED",
            "Decision recorded",
            BigDecimal.valueOf(100.0),
            null,
            null,
            null,
            Instant.now(),
            "{}",
            Instant.now());

    useCase.execute(command);

    verify(dispatcher).dispatch(command);
  }
}

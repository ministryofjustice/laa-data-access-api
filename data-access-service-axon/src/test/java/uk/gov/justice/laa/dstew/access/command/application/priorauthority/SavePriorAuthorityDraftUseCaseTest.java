package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityReadModel;

class SavePriorAuthorityDraftUseCaseTest {

  private RetryingCommandDispatcher dispatcher;
  private SubscriptionProjectionGateway projectionGateway;
  private SavePriorAuthorityDraftUseCase useCase;

  @BeforeEach
  void setUp() {
    dispatcher = mock(RetryingCommandDispatcher.class);
    projectionGateway = mock(SubscriptionProjectionGateway.class);
    useCase = new SavePriorAuthorityDraftUseCase(dispatcher, projectionGateway);
  }

  @Test
  void givenCommand_whenCreateAndProjectionConfirmed_thenReturnsTrue() {
    SavePriorAuthorityDraftCommand command = stubCommand();
    when(projectionGateway.awaitProjection(any(), eq(PriorAuthorityReadModel.class), any()))
        .thenReturn(true);

    boolean result = useCase.create(command);

    assertThat(result).isTrue();
  }

  @Test
  void givenCommand_whenCreateAndProjectionTimeout_thenReturnsFalse() {
    SavePriorAuthorityDraftCommand command = stubCommand();
    when(projectionGateway.awaitProjection(any(), eq(PriorAuthorityReadModel.class), any()))
        .thenReturn(false);

    boolean result = useCase.create(command);

    assertThat(result).isFalse();
  }

  @Test
  void givenCommand_whenUpdate_thenDispatchesCommandDirectly() {
    SavePriorAuthorityDraftCommand command = stubCommand();

    useCase.update(command);

    verify(dispatcher).dispatch(command);
  }

  private static SavePriorAuthorityDraftCommand stubCommand() {
    return new SavePriorAuthorityDraftCommand(
        UUID.randomUUID(), UUID.randomUUID(), null, "{}", 1, "PriorAuthority.json", Instant.now());
  }
}

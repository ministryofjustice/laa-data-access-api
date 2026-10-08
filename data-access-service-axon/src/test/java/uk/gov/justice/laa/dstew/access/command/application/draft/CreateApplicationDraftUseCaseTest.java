package uk.gov.justice.laa.dstew.access.command.application.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationProjectionExistsQuery;

class CreateApplicationDraftUseCaseTest {

  private RetryingCommandDispatcher dispatcher;
  private SubscriptionProjectionGateway projectionGateway;
  private CreateApplicationDraftUseCase useCase;

  @BeforeEach
  void setUp() {
    dispatcher = mock(RetryingCommandDispatcher.class);
    projectionGateway = mock(SubscriptionProjectionGateway.class);
    useCase = new CreateApplicationDraftUseCase(dispatcher, projectionGateway);
  }

  @Test
  void givenProjectionConfirmed_whenExecute_thenReturnsTrue() {
    CreateApplicationDraftCommand command = stubCommand();
    when(projectionGateway.awaitProjection(any(), any())).thenReturn(true);

    boolean result = useCase.execute(command);

    assertThat(result).isTrue();
    verify(projectionGateway)
        .awaitProjection(eq(new ApplicationProjectionExistsQuery(command.applicationId())), any());
  }

  @Test
  void givenProjectionTimeout_whenExecute_thenReturnsFalse() {
    CreateApplicationDraftCommand command = stubCommand();
    when(projectionGateway.awaitProjection(any(), any())).thenReturn(false);

    boolean result = useCase.execute(command);

    assertThat(result).isFalse();
  }

  @Test
  void givenProjection_whenExecute_thenDispatchesViaRetryingDispatcher() {
    CreateApplicationDraftCommand command = stubCommand();
    doAnswer(
            invocation -> {
              Runnable action = invocation.getArgument(1);
              action.run();
              return true;
            })
        .when(projectionGateway)
        .awaitProjection(any(), any());

    useCase.execute(command);

    verify(dispatcher).dispatch(command);
  }

  private CreateApplicationDraftCommand stubCommand() {
    UUID id = UUID.randomUUID();
    return new CreateApplicationDraftCommand(
        id,
        "APPLICATION_IN_PROGRESS",
        "LAA-123",
        Map.of("id", id.toString()),
        "{}",
        1,
        "BaseCivilApplication.json",
        Instant.now(),
        List.of());
  }
}

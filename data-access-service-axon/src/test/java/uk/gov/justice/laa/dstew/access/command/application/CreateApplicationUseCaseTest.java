package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;

class CreateApplicationUseCaseTest {

  private RetryingCommandDispatcher dispatcher;
  private SubscriptionProjectionGateway projectionGateway;
  private CreateApplicationUseCase useCase;

  @BeforeEach
  void setUp() {
    dispatcher = mock(RetryingCommandDispatcher.class);
    projectionGateway = mock(SubscriptionProjectionGateway.class);
    useCase = new CreateApplicationUseCase(dispatcher, projectionGateway);
  }

  @Test
  void givenProjectionConfirmed_whenExecute_thenReturnsTrue() {
    CreateApplicationCommand command = stubCommand();
    when(projectionGateway.awaitProjection(any(), eq(ApplicationReadModel.class), any()))
        .thenReturn(true);

    boolean result = useCase.execute(command);

    assertThat(result).isTrue();
    verify(projectionGateway)
        .awaitProjection(
            eq(new FindApplicationByIdQuery(command.applicationId())),
            eq(ApplicationReadModel.class),
            any());
  }

  @Test
  void givenProjectionTimeout_whenExecute_thenReturnsFalse() {
    CreateApplicationCommand command = stubCommand();
    when(projectionGateway.awaitProjection(any(), eq(ApplicationReadModel.class), any()))
        .thenReturn(false);

    boolean result = useCase.execute(command);

    assertThat(result).isFalse();
  }

  @Test
  void givenProjection_whenExecute_thenDispatchesViaRetryingDispatcher() {
    CreateApplicationCommand command = stubCommand();
    doAnswer(
            invocation -> {
              Runnable action = invocation.getArgument(2);
              action.run();
              return true;
            })
        .when(projectionGateway)
        .awaitProjection(any(), eq(ApplicationReadModel.class), any());

    useCase.execute(command);

    verify(dispatcher).dispatch(command);
  }

  private CreateApplicationCommand stubCommand() {
    UUID id = UUID.randomUUID();
    return new CreateApplicationCommand(
        id,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        Map.of("id", id.toString()),
        "{}",
        1,
        "ApplyApplication.json");
  }
}

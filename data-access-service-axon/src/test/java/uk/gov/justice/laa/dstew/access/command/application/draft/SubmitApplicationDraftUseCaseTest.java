package uk.gov.justice.laa.dstew.access.command.application.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.query.ProjectionReadinessGateway;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationSubmissionProjectedQuery;

class SubmitApplicationDraftUseCaseTest {

  private RetryingCommandDispatcher dispatcher;
  private ProjectionReadinessGateway projectionGateway;
  private SubmitApplicationDraftUseCase useCase;

  @BeforeEach
  void setUp() {
    dispatcher = mock(RetryingCommandDispatcher.class);
    projectionGateway = mock(ProjectionReadinessGateway.class);
    useCase = new SubmitApplicationDraftUseCase(dispatcher, projectionGateway);
  }

  @Test
  void givenProjectionConfirmed_whenSubmit_thenReturnsTrue() {
    SubmitApplicationDraftCommand command = stubCommand();
    when(projectionGateway.awaitProjection(any(), any())).thenReturn(true);

    boolean result = useCase.submit(command);

    assertThat(result).isTrue();
    verify(projectionGateway)
        .awaitProjection(
            eq(new ApplicationSubmissionProjectedQuery(command.applicationId())), any());
  }

  @Test
  void givenProjectionTimeout_whenSubmit_thenReturnsFalse() {
    SubmitApplicationDraftCommand command = stubCommand();
    when(projectionGateway.awaitProjection(any(), any())).thenReturn(false);

    boolean result = useCase.submit(command);

    assertThat(result).isFalse();
  }

  @Test
  void givenProjection_whenSubmit_thenDispatchesViaRetryingDispatcher() {
    SubmitApplicationDraftCommand command = stubCommand();
    doAnswer(
            invocation -> {
              Runnable action = invocation.getArgument(1);
              action.run();
              return true;
            })
        .when(projectionGateway)
        .awaitProjection(any(), any());

    useCase.submit(command);

    verify(dispatcher).dispatch(command);
  }

  private SubmitApplicationDraftCommand stubCommand() {
    return new SubmitApplicationDraftCommand(UUID.randomUUID(), Instant.now());
  }
}

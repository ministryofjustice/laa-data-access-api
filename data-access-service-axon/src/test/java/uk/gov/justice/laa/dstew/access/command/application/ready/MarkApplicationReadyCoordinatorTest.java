package uk.gov.justice.laa.dstew.access.command.application.ready;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.InheritLinkedGroupAssignmentCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;

@ExtendWith(MockitoExtension.class)
class MarkApplicationReadyCoordinatorTest {
  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Mock private RetryingCommandDispatcher dispatcher;
  @Mock private ApplicationGroupRouteResolver groupRouteResolver;
  @InjectMocks private MarkApplicationReadyCoordinator coordinator;

  @Test
  void givenStandaloneApplication_whenReadyRecorded_thenNoGroupCommand() {
    UUID applicationId = UUID.randomUUID();
    MarkApplicationReadyCommand command = command(applicationId);
    when(groupRouteResolver.lockGroupRoutesForReadiness(applicationId))
        .thenReturn(Optional.empty());
    when(dispatcher.dispatch(command, ReadyApplicationResult.class))
        .thenReturn(ReadyApplicationResult.RECORDED);

    assertThat(coordinator.handle(command)).isEqualTo(ReadyApplicationResult.RECORDED);

    InOrder inOrder = inOrder(groupRouteResolver, dispatcher);
    inOrder.verify(groupRouteResolver).lockGroupRoutesForReadiness(applicationId);
    inOrder.verify(dispatcher).dispatch(command, ReadyApplicationResult.class);
    verifyNoMoreInteractions(dispatcher);
  }

  @Test
  void givenGroupedApplication_whenReadyRecorded_thenGroupInherits() {
    UUID applicationId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    MarkApplicationReadyCommand command = command(applicationId);
    when(groupRouteResolver.lockGroupRoutesForReadiness(applicationId))
        .thenReturn(Optional.of(groupId));
    when(dispatcher.dispatch(command, ReadyApplicationResult.class))
        .thenReturn(ReadyApplicationResult.RECORDED);

    assertThat(coordinator.handle(command)).isEqualTo(ReadyApplicationResult.RECORDED);

    InOrder inOrder = inOrder(groupRouteResolver, dispatcher);
    inOrder.verify(groupRouteResolver).lockGroupRoutesForReadiness(applicationId);
    inOrder.verify(dispatcher).dispatch(command, ReadyApplicationResult.class);
    inOrder
        .verify(dispatcher)
        .dispatch(new InheritLinkedGroupAssignmentCommand(groupId, applicationId, OCCURRED_AT));
  }

  @Test
  void givenGroupedApplication_whenAlreadyRecorded_thenNoGroupCommand() {
    UUID applicationId = UUID.randomUUID();
    MarkApplicationReadyCommand command = command(applicationId);
    when(groupRouteResolver.lockGroupRoutesForReadiness(applicationId))
        .thenReturn(Optional.of(UUID.randomUUID()));
    when(dispatcher.dispatch(command, ReadyApplicationResult.class))
        .thenReturn(ReadyApplicationResult.ALREADY_RECORDED);

    assertThat(coordinator.handle(command)).isEqualTo(ReadyApplicationResult.ALREADY_RECORDED);

    verify(dispatcher).dispatch(command, ReadyApplicationResult.class);
    verifyNoMoreInteractions(dispatcher);
  }

  private MarkApplicationReadyCommand command(UUID applicationId) {
    return new MarkApplicationReadyCommand(applicationId, "{}", OCCURRED_AT);
  }
}

package uk.gov.justice.laa.dstew.access.command.application.ready;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;

@ExtendWith(MockitoExtension.class)
class MarkApplicationReadyCoordinatorTest {
  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final UUID GROUP_ID = UUID.randomUUID();
  private static final Instant OCCURRED_AT = Instant.parse("2026-10-27T12:00:00Z");

  @Mock private ApplicationGroupRouteResolver groupRouteResolver;
  @Mock private RetryingCommandDispatcher dispatcher;

  @InjectMocks private MarkApplicationReadyCoordinator coordinator;

  @Test
  void givenGroupMember_whenHandling_thenLocksBeforeDispatchWithGroupId() {
    var command = command();
    when(groupRouteResolver.lockForAssignment(APPLICATION_ID)).thenReturn(Optional.of(GROUP_ID));

    coordinator.handle(command);

    var inOrder = inOrder(groupRouteResolver, dispatcher);
    inOrder.verify(groupRouteResolver).lockForAssignment(APPLICATION_ID);
    inOrder
        .verify(dispatcher)
        .dispatch(command.withLinkedGroupId(GROUP_ID), ReadyApplicationResult.class);
  }

  @Test
  void givenStandalone_whenHandling_thenDispatchesWithoutGroupId() {
    var command = command();
    when(groupRouteResolver.lockForAssignment(APPLICATION_ID)).thenReturn(Optional.empty());

    coordinator.handle(command);

    var inOrder = inOrder(groupRouteResolver, dispatcher);
    inOrder.verify(groupRouteResolver).lockForAssignment(APPLICATION_ID);
    inOrder
        .verify(dispatcher)
        .dispatch(command.withLinkedGroupId(null), ReadyApplicationResult.class);
  }

  private MarkApplicationReadyCommand command() {
    return new MarkApplicationReadyCommand(APPLICATION_ID, 0L, "{}", OCCURRED_AT);
  }
}

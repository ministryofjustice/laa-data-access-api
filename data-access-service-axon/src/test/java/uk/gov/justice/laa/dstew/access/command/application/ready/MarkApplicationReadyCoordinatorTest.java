package uk.gov.justice.laa.dstew.access.command.application.ready;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.ConcurrencyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;

@ExtendWith(MockitoExtension.class)
class MarkApplicationReadyCoordinatorTest {
  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final UUID GROUP_ID = UUID.randomUUID();
  private static final Instant OCCURRED_AT = Instant.parse("2026-10-27T12:00:00Z");

  @Mock private ApplicationGroupRouteResolver groupRouteResolver;
  @Mock private CommandGateway commandGateway;
  @Mock private PlatformTransactionManager transactionManager;

  private MarkApplicationReadyCoordinator coordinator;

  @BeforeEach
  void setUp() {
    when(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .thenAnswer(invocation -> new SimpleTransactionStatus());
    coordinator =
        new MarkApplicationReadyCoordinator(
            groupRouteResolver,
            new RetryingCommandDispatcher(commandGateway),
            commandGateway,
            transactionManager);
  }

  @Test
  void givenGroupMember_whenHandling_thenLocksBeforeDispatchWithGroupId() {
    var command = command();
    when(groupRouteResolver.lockForAssignment(APPLICATION_ID)).thenReturn(Optional.of(GROUP_ID));
    when(commandGateway.sendAndWait(
            command.withLinkedGroupId(GROUP_ID), ReadyApplicationResult.class))
        .thenReturn(ReadyApplicationResult.RECORDED);

    var result = coordinator.handle(command);

    assertThat(result).isEqualTo(ReadyApplicationResult.RECORDED);
    InOrder inOrder = inOrder(groupRouteResolver, commandGateway);
    inOrder.verify(groupRouteResolver).lockForAssignment(APPLICATION_ID);
    inOrder
        .verify(commandGateway)
        .sendAndWait(command.withLinkedGroupId(GROUP_ID), ReadyApplicationResult.class);
  }

  @Test
  void givenStandalone_whenHandling_thenDispatchesWithoutGroupId() {
    var command = command();
    when(groupRouteResolver.lockForAssignment(APPLICATION_ID)).thenReturn(Optional.empty());
    when(commandGateway.sendAndWait(command.withLinkedGroupId(null), ReadyApplicationResult.class))
        .thenReturn(ReadyApplicationResult.RECORDED);

    coordinator.handle(command);

    InOrder inOrder = inOrder(groupRouteResolver, commandGateway);
    inOrder.verify(groupRouteResolver).lockForAssignment(APPLICATION_ID);
    inOrder
        .verify(commandGateway)
        .sendAndWait(command.withLinkedGroupId(null), ReadyApplicationResult.class);
  }

  @Test
  void givenAppendConflict_whenHandling_thenRetriesInFreshTransactionAndRelocksRoutes() {
    var command = command();
    when(groupRouteResolver.lockForAssignment(APPLICATION_ID))
        .thenReturn(Optional.of(GROUP_ID), Optional.of(GROUP_ID));
    when(commandGateway.sendAndWait(
            command.withLinkedGroupId(GROUP_ID), ReadyApplicationResult.class))
        .thenThrow(new RuntimeException("append conflict", new ConcurrencyException("conflict")))
        .thenReturn(ReadyApplicationResult.RECORDED);

    var result = coordinator.handle(command);

    assertThat(result).isEqualTo(ReadyApplicationResult.RECORDED);
    verify(transactionManager, times(2)).getTransaction(any(TransactionDefinition.class));
    verify(transactionManager).rollback(any(TransactionStatus.class));
    verify(transactionManager).commit(any(TransactionStatus.class));
    verify(groupRouteResolver, times(2)).lockForAssignment(APPLICATION_ID);
    verify(commandGateway, times(2))
        .sendAndWait(command.withLinkedGroupId(GROUP_ID), ReadyApplicationResult.class);
  }

  private MarkApplicationReadyCommand command() {
    return new MarkApplicationReadyCommand(APPLICATION_ID, 0L, "{}", OCCURRED_AT);
  }
}

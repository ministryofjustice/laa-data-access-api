package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.AddApplicationToLinkedGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ChangeLinkedGroupLeadCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.EstablishLinkedApplicationGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.RemoveApplicationFromLinkedGroupCommand;

@ExtendWith(MockitoExtension.class)
class RouteLockGuardDispatchInterceptorTest {
  @Mock private MessageDispatchInterceptorChain<CommandMessage> chain;

  private RouteLockGuardDispatchInterceptor interceptor;

  @BeforeEach
  void setUp() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    interceptor = new RouteLockGuardDispatchInterceptor();
  }

  @AfterEach
  void clearTransaction() {
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(
            synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN));
    TransactionSynchronizationManager.clear();
  }

  @ParameterizedTest
  @MethodSource("guardedCommands")
  void givenNoLocks_whenDispatching_thenFailsWithoutProceeding(Object payload) {
    var stream = interceptor.interceptOnDispatch(message(payload), null, chain);

    assertThat(stream.error())
        .hasValueSatisfying(
            error ->
                assertThat(error)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("requires linked-group route locks"));
    verifyNoInteractions(chain);
  }

  @ParameterizedTest
  @MethodSource("guardedCommands")
  void givenRequiredLocks_whenDispatching_thenProceeds(Object payload) {
    recordRequiredLocks(payload);
    var command = message(payload);

    interceptor.interceptOnDispatch(command, null, chain);

    verify(chain).proceed(command, null);
  }

  @Test
  void givenUnguardedCommand_whenDispatching_thenProceeds() {
    var command = message("unrelated");

    interceptor.interceptOnDispatch(command, null, chain);

    verify(chain).proceed(command, null);
  }

  private static Stream<Arguments> guardedCommands() {
    var groupId = UUID.randomUUID();
    var firstId = UUID.randomUUID();
    var secondId = UUID.randomUUID();
    var occurredAt = Instant.parse("2026-09-14T09:00:00Z");
    return Stream.of(
        Arguments.of(
            new EstablishLinkedApplicationGroupCommand(
                groupId, firstId, List.of(firstId, secondId), occurredAt)),
        Arguments.of(new AddApplicationToLinkedGroupCommand(groupId, firstId, 0L, occurredAt)),
        Arguments.of(new RemoveApplicationFromLinkedGroupCommand(groupId, firstId, 0L, occurredAt)),
        Arguments.of(new ChangeLinkedGroupLeadCommand(groupId, secondId, 0L, occurredAt)));
  }

  private void recordRequiredLocks(Object payload) {
    switch (payload) {
      case EstablishLinkedApplicationGroupCommand command ->
          RouteLockRegistry.recordApplications(command.memberApplicationIds());
      case AddApplicationToLinkedGroupCommand command -> {
        RouteLockRegistry.recordGroup(command.groupId());
        RouteLockRegistry.recordApplications(List.of(command.applicationId()));
      }
      case RemoveApplicationFromLinkedGroupCommand command ->
          RouteLockRegistry.recordGroup(command.groupId());
      case ChangeLinkedGroupLeadCommand command -> RouteLockRegistry.recordGroup(command.groupId());
      default -> throw new IllegalArgumentException("Unexpected command");
    }
  }

  private CommandMessage message(Object payload) {
    return new GenericCommandMessage(new MessageType(payload.getClass()), payload);
  }
}

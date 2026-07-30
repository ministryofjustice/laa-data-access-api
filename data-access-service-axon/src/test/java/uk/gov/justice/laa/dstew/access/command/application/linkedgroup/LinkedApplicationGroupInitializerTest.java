package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;

class LinkedApplicationGroupInitializerTest {

  @Test
  void givenGroupRequested_whenHandled_thenDispatchesInitialisationCommand() {
    CommandGateway commandGateway = mock(CommandGateway.class);
    LinkedApplicationGroupInitializer initializer =
        new LinkedApplicationGroupInitializer(commandGateway);
    UUID groupId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    List<UUID> members = List.of(leadApplicationId, UUID.randomUUID());
    Instant occurredAt = Instant.parse("2026-07-15T08:00:00Z");

    initializer.on(
        new LinkedApplicationGroupRequested(groupId, leadApplicationId, members, occurredAt));

    verify(commandGateway)
        .sendAndWait(
            new InitialiseLinkedApplicationGroupCommand(
                groupId, leadApplicationId, members, occurredAt));
  }
}

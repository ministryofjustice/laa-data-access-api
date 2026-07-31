package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;

/** Initializes linked groups after the requesting application transaction has committed. */
@Component
@Namespace("linked-application-group-initializer")
public class LinkedApplicationGroupInitializer {

  private final CommandGateway commandGateway;

  public LinkedApplicationGroupInitializer(CommandGateway commandGateway) {
    this.commandGateway = commandGateway;
  }

  /** Dispatches the idempotent group initialization command. */
  @EventHandler
  public void on(LinkedApplicationGroupRequested event) {
    commandGateway.sendAndWait(
        new InitialiseLinkedApplicationGroupCommand(
            event.groupId(),
            event.leadApplicationId(),
            event.memberApplicationIds(),
            event.occurredAt()));
  }
}

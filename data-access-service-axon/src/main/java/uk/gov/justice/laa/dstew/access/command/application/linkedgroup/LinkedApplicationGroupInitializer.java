package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;

/** Establishes linked groups after the requesting application transaction has committed. */
@Component
@Namespace("linked-application-group-initializer")
public class LinkedApplicationGroupInitializer {

  private final CommandGateway commandGateway;

  public LinkedApplicationGroupInitializer(CommandGateway commandGateway) {
    this.commandGateway = commandGateway;
  }

  /** Dispatches the idempotent group establishment command. */
  @EventHandler
  public void on(LinkedApplicationGroupRequested event) {
    var memberApplicationIds = event.memberApplicationIds();
    if (memberApplicationIds.size() == 2
        && event.leadApplicationId() != null
        && memberApplicationIds.contains(event.leadApplicationId())) {
      var memberApplicationId =
          memberApplicationIds.stream()
              .filter(applicationId -> !applicationId.equals(event.leadApplicationId()))
              .findFirst()
              .orElseThrow();
      try {
        commandGateway.sendAndWait(
            new AddApplicationToLinkedGroupCommand(
                event.groupId(), memberApplicationId, event.occurredAt()));
        return;
      } catch (IllegalStateException ignored) {
        // The legacy create-time linking flow still raises this event before the group exists.
      }
    }

    commandGateway.sendAndWait(
        new EstablishLinkedApplicationGroupCommand(
            event.groupId(), event.leadApplicationId(), memberApplicationIds, event.occurredAt()));
  }
}

package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.List;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;

/**
 * Wires {@link ApplicationCreatedEvent} to the {@link LinkedApplicationGroupAggregate} without a
 * saga.
 *
 * <p>This component uses the {@code linked-application-group-router} processing group, which is
 * configured as a <em>subscribing</em> processor in {@code application.yml}. Subscribing processors
 * run synchronously in the same thread and Axon unit-of-work as the originating command. This
 * means:
 *
 * <ul>
 *   <li>If the lead application does not exist, {@link CreateLinkedApplicationGroupCommand} throws
 *       {@link uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException}, the unit of
 *       work rolls back, and the caller receives a 404 — preserving the current synchronous
 *       validation contract.
 *   <li>If the lead exists, {@link LinkedApplicationGroupRequested} is emitted on the lead's event
 *       stream and the group is initialised in the same transaction.
 * </ul>
 *
 * <p>This component is not a saga: it holds no persistent state and dispatches commands directly.
 */
@Component
@ProcessingGroup("linked-application-group-router")
public class ApplicationGroupEventRouter {

  private final ObjectProvider<CommandGateway> commandGatewayProvider;

  public ApplicationGroupEventRouter(ObjectProvider<CommandGateway> commandGatewayProvider) {
    this.commandGatewayProvider = commandGatewayProvider;
  }

  /**
   * When an Application is created with a lead link, validates that any other referenced associated
   * applications exist, then dispatches a command to prove the lead exists and initiate group
   * formation.
   *
   * <p>Other associated applications (entries in {@code allLinkedApplications} whose {@code
   * associatedApplicationId} is neither the current application nor the lead) are validated first
   * via {@link ValidateApplicationExistsCommand}. This preserves the existing 404 behaviour for
   * missing associated applications that was previously enforced by {@code
   * ApplicationCreationDetailsFactory.requireExistingApplicationStream()}.
   */
  @EventHandler
  public void on(ApplicationCreatedEvent event) {
    if (event.leadApplicationId() == null) {
      return; // standalone application — no group needed
    }

    // Validate other referenced associated applications before touching the lead.
    validateOtherAssociatedApplications(event);

    List<UUID> members =
        event.applicationId().equals(event.leadApplicationId())
            ? List.of(event.leadApplicationId())
            : List.of(event.leadApplicationId(), event.applicationId());

    commandGatewayProvider
        .getObject()
        .sendAndWait(
            new CreateLinkedApplicationGroupCommand(
                event.leadApplicationId(), event.applicationId(), members, event.occurredAt()));
  }

  /**
   * When the lead aggregate acknowledges a group-formation request, dispatches the initialisation
   * command to the {@link LinkedApplicationGroupAggregate}.
   */
  @EventHandler
  public void on(LinkedApplicationGroupRequested event) {
    commandGatewayProvider
        .getObject()
        .sendAndWait(
            new InitialiseLinkedApplicationGroupCommand(
                event.groupId(),
                event.leadApplicationId(),
                event.memberApplicationIds(),
                event.occurredAt()));
  }

  /**
   * Dispatches {@link ValidateApplicationExistsCommand} for each associated application referenced
   * in the event content that is neither the current application nor the lead (the lead is
   * validated separately by targeting it with {@link CreateLinkedApplicationGroupCommand}).
   */
  private void validateOtherAssociatedApplications(ApplicationCreatedEvent event) {
    var associatedApplicationIds = event.associatedApplicationIds();
    if (associatedApplicationIds.isEmpty()) {
      return;
    }
    associatedApplicationIds.stream()
        .filter(id -> !id.equals(event.applicationId()))
        .filter(id -> !id.equals(event.leadApplicationId()))
        .distinct()
        .forEach(
            id ->
                commandGatewayProvider
                    .getObject()
                    .sendAndWait(new ValidateApplicationExistsCommand(id)));
  }
}

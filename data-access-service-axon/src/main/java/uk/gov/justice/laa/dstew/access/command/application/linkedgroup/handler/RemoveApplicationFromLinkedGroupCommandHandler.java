package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler;

import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupJustificationStore;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.RemoveApplicationFromLinkedGroupCommand;

/** Handles removing an application from its linked group. */
@Component
@RequiredArgsConstructor
public class RemoveApplicationFromLinkedGroupCommandHandler {
  private final LinkedGroupJustificationStore justificationStore;

  /** Removes a non-lead member from an established group. */
  @CommandHandler
  public void handle(
      RemoveApplicationFromLinkedGroupCommand command,
      @InjectEntity(idProperty = "groupId") @Nullable LinkedApplicationGroupAggregate group,
      EventAppender eventAppender) {
    LinkedApplicationGroupDecider.decideRemoveApplication(
            LinkedApplicationGroupAggregate.stateOf(group), command)
        .ifPresent(
            event ->
                eventAppender.append(
                    event.withJustification(
                        justificationStore.save(
                            command.groupId(), command.justification(), command.occurredAt()))));
  }
}

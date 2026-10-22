package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedGroupRemovalResult;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.RemoveApplicationFromLinkedGroupCommand;

/** Handles removing an application from its linked group. */
@Component
public class RemoveApplicationFromLinkedGroupCommandHandler {

  /** Removes a non-lead member from an established group. */
  @CommandHandler
  public LinkedGroupRemovalResult handle(
      RemoveApplicationFromLinkedGroupCommand command,
      @InjectEntity(idProperty = "groupId") @Nullable LinkedApplicationGroupAggregate group,
      EventAppender eventAppender) {
    var removal =
        LinkedApplicationGroupDecider.decideRemoveApplication(
            LinkedApplicationGroupAggregate.stateOf(group), command);
    eventAppender.append(removal);
    return LinkedApplicationGroupDecider.removalResult(removal);
  }
}

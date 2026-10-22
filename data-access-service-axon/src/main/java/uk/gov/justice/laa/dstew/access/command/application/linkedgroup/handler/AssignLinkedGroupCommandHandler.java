package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.AssignLinkedGroupCommand;

/** Handles assigning a linked group's shared work-item assignment. */
@Component
public class AssignLinkedGroupCommandHandler {

  /** Assigns the group after validating membership and assignment version. */
  @CommandHandler
  public void handle(
      AssignLinkedGroupCommand command,
      @InjectEntity(idProperty = "groupId") @Nullable LinkedApplicationGroupAggregate group,
      EventAppender eventAppender) {
    eventAppender.append(
        LinkedApplicationGroupDecider.decideAssign(
            LinkedApplicationGroupAggregate.stateOf(group), command));
  }
}

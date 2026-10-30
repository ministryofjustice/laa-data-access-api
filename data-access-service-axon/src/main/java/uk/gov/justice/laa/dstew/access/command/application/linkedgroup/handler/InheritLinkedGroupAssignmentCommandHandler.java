package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.InheritLinkedGroupAssignmentCommand;

/** Handles recording a ready member's inherited group assignment. */
@Component
public class InheritLinkedGroupAssignmentCommandHandler {

  /** Records the inherited assignment when the group currently has a caseworker. */
  @CommandHandler
  public void handle(
      InheritLinkedGroupAssignmentCommand command,
      @InjectEntity(idProperty = "groupId") @Nullable LinkedApplicationGroupAggregate group,
      EventAppender eventAppender) {
    LinkedApplicationGroupDecider.decideInherit(
            LinkedApplicationGroupAggregate.stateOf(group), command)
        .ifPresent(eventAppender::append);
  }
}

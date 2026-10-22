package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.modelling.annotation.InjectEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ValidateLinkedGroupAssigneeCommand;

/** Handles validating ownership of a linked group's current work-item assignment. */
@Component
public class ValidateLinkedGroupAssigneeCommandHandler {

  /** Validates the supplied caseworker without appending events. */
  @CommandHandler
  public void handle(
      ValidateLinkedGroupAssigneeCommand command,
      @InjectEntity(idProperty = "groupId") @Nullable LinkedApplicationGroupAggregate group) {
    LinkedApplicationGroupDecider.validateAssignee(
        LinkedApplicationGroupAggregate.stateOf(group), command);
  }
}

package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ChangeLinkedGroupLeadCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDecider;

/** Handles changing a linked group's lead. */
@Component
public class ChangeLinkedGroupLeadCommandHandler {

  /** Changes the lead of an established group. */
  @CommandHandler
  public void handle(
      ChangeLinkedGroupLeadCommand command,
      @InjectEntity(idProperty = "groupId") LinkedApplicationGroupAggregate group,
      EventAppender eventAppender) {
    LinkedApplicationGroupDecider.decideChangeLead(group.getState(), command)
        .ifPresent(eventAppender::append);
  }
}

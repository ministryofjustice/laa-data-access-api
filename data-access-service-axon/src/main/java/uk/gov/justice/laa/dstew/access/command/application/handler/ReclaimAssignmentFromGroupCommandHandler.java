package uk.gov.justice.laa.dstew.access.command.application.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ReclaimAssignmentFromGroupCommand;

/** Handles reclaiming assignment ownership after an application leaves its linked group. */
@Component
public class ReclaimAssignmentFromGroupCommandHandler {

  /** Records return of assignment ownership to the application. */
  @CommandHandler
  public void handle(
      ReclaimAssignmentFromGroupCommand command,
      @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
      EventAppender eventAppender) {
    ApplicationCommandHandlerSupport.requireApplicationExists(application, command.applicationId());
    ApplicationDecider.decideReclaimFromGroup(application.getState(), command)
        .ifPresent(eventAppender::append);
  }
}

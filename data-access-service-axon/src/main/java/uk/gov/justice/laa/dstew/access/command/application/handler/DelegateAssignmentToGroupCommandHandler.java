package uk.gov.justice.laa.dstew.access.command.application.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.DelegateAssignmentToGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.DelegatedAssignment;

/** Handles delegation of an application's assignment to a linked group. */
@Component
public class DelegateAssignmentToGroupCommandHandler {

  /** Records group ownership and returns the application assignment being handed over. */
  @CommandHandler
  public DelegatedAssignment handle(
      DelegateAssignmentToGroupCommand command,
      @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
      EventAppender eventAppender) {
    ApplicationCommandHandlerSupport.requireApplicationExists(application, command.applicationId());
    var handedOver = ApplicationDecider.delegatedAssignment(application.getState());
    ApplicationDecider.decideDelegateToGroup(application.getState(), command)
        .ifPresent(eventAppender::append);
    return handedOver;
  }
}

package uk.gov.justice.laa.dstew.access.command.application.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCommand;

/** Handles manual application decisions. */
@Component
public class MakeApplicationDecisionCommandHandler {

  /** Validates and records a manual decision for an application. */
  @CommandHandler
  public void handle(
      MakeApplicationDecisionCommand command,
      @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
      EventAppender eventAppender) {
    ApplicationCommandHandlerSupport.requireApplicationExists(application, command.applicationId());
    ApplicationState state = application.getState();
    ApplicationDecider.validateManualDecisionAssignment(state, command);
    ApplicationCommandHandlerSupport.requireContentRetained(application);
    recordDecision(command, AutoGrantedState.MANUAL, application, eventAppender);
  }

  static void recordDecision(
      MakeApplicationDecisionCommand command,
      AutoGrantedState autoGranted,
      ApplicationAggregate application,
      EventAppender eventAppender) {
    ApplicationDecisionMadeEvent decision =
        ApplicationDecider.decideDecision(application.getState(), command, autoGranted);
    eventAppender.append(decision);
  }
}

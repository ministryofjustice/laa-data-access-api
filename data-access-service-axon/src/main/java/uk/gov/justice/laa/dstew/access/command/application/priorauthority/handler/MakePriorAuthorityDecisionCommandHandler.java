package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDecider;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Handles decisions on prior-authority requests. */
@Component
public class MakePriorAuthorityDecisionCommandHandler {

  /** Makes a decision on a prior-authority request and persists the decision. */
  @CommandHandler
  public void handle(
      MakePriorAuthorityDecisionCommand command,
      @InjectEntity(idProperty = "priorAuthorityId") PriorAuthorityAggregate priorAuthority,
      EventAppender eventAppender) {

    requirePriorAuthorityExists(priorAuthority, command);

    PriorAuthorityDecider.decideDecision(priorAuthority.getState(), command)
        .ifPresent(eventAppender::append);
  }

  private static void requirePriorAuthorityExists(
      PriorAuthorityAggregate priorAuthority, MakePriorAuthorityDecisionCommand command) {
    if (priorAuthority.getPriorAuthorityId() == null
        || !command.priorAuthorityId().equals(priorAuthority.getPriorAuthorityId())) {
      throw new ResourceNotFoundException(
          "No prior authority found with ID: " + command.priorAuthorityId());
    }
  }
}

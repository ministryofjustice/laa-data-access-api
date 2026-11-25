package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.axonframework.modelling.repository.EntityNotFoundException;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDecider;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Handles decisions on prior-authority requests. */
@Component
public class MakePriorAuthorityDecisionCommandHandler {

  /** Makes a decision on a prior-authority request and persists the decision. */
  @CommandHandler
  public void handle(
      MakePriorAuthorityDecisionCommand command,
      PriorAuthorityDataStore dataStore,
      @InjectEntity(idProperty = "priorAuthorityId") PriorAuthorityAggregate priorAuthority,
      EventAppender eventAppender) {

    requirePriorAuthorityExists(priorAuthority, command);

    PriorAuthorityDecider.decideDecision(priorAuthority.getState(), command)
        .ifPresent(
            event -> {
              PriorAuthorityDataPayload current = requireSubmittedContent(priorAuthority);
              dataStore.append(
                  event.priorAuthorityId(),
                  event.dataVersion(),
                  event.applicationId(),
                  current.withDecision(
                      new PriorAuthorityDataPayload.DecisionDetails(
                          event.overallDecision(),
                          command.decisionJustification(),
                          command.amountGranted(),
                          command.dateGranted(),
                          command.expertFee(),
                          command.disbursementInformation(),
                          command.apportionmentInformation(),
                          command.serialisedRequest())),
                  command.serialisedRequest(),
                  command.occurredAt());
              eventAppender.append(event);
            });
  }

  /**
   * Returns the submitted content folded from the event stream. The decider has already applied
   * every rule from kept fields, so this is checked only when a new version must be written: a
   * submission whose content has been erased is treated as missing, with the same not-found error
   * Axon raises for an entity that does not exist.
   */
  private static PriorAuthorityDataPayload requireSubmittedContent(
      PriorAuthorityAggregate priorAuthority) {
    return priorAuthority
        .getState()
        .submittedPayload()
        .orElseThrow(() -> new EntityNotFoundException(priorAuthority.getPriorAuthorityId()));
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

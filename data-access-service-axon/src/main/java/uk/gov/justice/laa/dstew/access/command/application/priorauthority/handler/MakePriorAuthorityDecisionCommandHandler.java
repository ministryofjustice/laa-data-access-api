package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDecider;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionCommand;

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

    PriorAuthorityDecider.decideDecision(priorAuthority.getState(), command)
        .ifPresent(
            event -> {
              PriorAuthorityDataPayload current =
                  dataStore.get(command.priorAuthorityId(), priorAuthority.getDataVersion());
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
}

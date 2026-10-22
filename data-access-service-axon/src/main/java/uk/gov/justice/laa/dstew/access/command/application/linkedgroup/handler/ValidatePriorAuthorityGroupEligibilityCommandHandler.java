package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler;

import jakarta.annotation.Nullable;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDecider;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupState;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ValidatePriorAuthorityGroupEligibilityCommand;

/**
 * Handles validating that an application may create a Prior Authority against its group's current
 * lead.
 */
@Component
public class ValidatePriorAuthorityGroupEligibilityCommandHandler {

  /** Validates eligibility without appending an event. */
  @CommandHandler
  public void handle(
      ValidatePriorAuthorityGroupEligibilityCommand command,
      @InjectEntity(idProperty = "groupId") @Nullable LinkedApplicationGroupAggregate group) {
    LinkedApplicationGroupDecider.validatePriorAuthorityGroupEligibility(
        group == null ? new LinkedApplicationGroupState() : group.getState(), command);
  }
}

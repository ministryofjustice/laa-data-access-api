package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import jakarta.annotation.Nullable;
import java.util.List;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDecider;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException;
import uk.gov.justice.laa.dstew.access.security.OfficeCodeWriteAccessPolicy;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Handles creation of prior-authority drafts. */
@Component
public class CreatePriorAuthorityDraftCommandHandler {

  /** Creates a new prior-authority draft and emits the corresponding event. */
  @CommandHandler
  public void handle(
      CreatePriorAuthorityDraftCommand command,
      @InjectEntity(idProperty = "priorAuthorityId") @Nullable PriorAuthorityAggregate existing,
      @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
      OfficeCodeWriteAccessPolicy writeAccessPolicy,
      EventAppender eventAppender) {

    if (existing != null && existing.getPriorAuthorityId() != null) {
      throw new PriorAuthorityCreationConflictException(command.priorAuthorityId());
    }

    if (!application.isGranted()) {
      throw new ValidationException(
          List.of(
              "Prior authority requires the application to have an overall decision of GRANTED"));
    }

    String officeCode = application.getOfficeCode();
    writeAccessPolicy.requireOfficeCodeWriteAccess(officeCode, command.caller());
    eventAppender.append(PriorAuthorityDecider.decideStartDraft(command, officeCode));
  }
}

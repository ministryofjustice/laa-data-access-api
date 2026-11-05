package uk.gov.justice.laa.dstew.access.command.application.handler;

import jakarta.annotation.Nullable;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetailsFactory;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

/** Handles creation of application drafts. */
@Component
public class CreateApplicationDraftCommandHandler {

  /** Creates a new application draft or handles an idempotent retry. */
  @CommandHandler
  public UUID handle(
      CreateApplicationDraftCommand command,
      @InjectEntity(idProperty = "applicationId") @Nullable ApplicationAggregate application,
      ApplicationCreationDetailsFactory factory,
      JsonSchemaValidator jsonSchemaValidator,
      EventAppender eventAppender) {
    jsonSchemaValidator.validate(
        command.applicationContent(), command.schemaName(), command.schemaVersion());
    String fingerprint = PayloadFingerprint.compute(command.serialisedRequest());
    if (application == null || application.getApplicationId() == null) {
      factory.validate(command.applicationContent());
      ApplicationDecider.decideStartDraft(
              new ApplicationState(),
              new ApplicationDraftStartedEvent(
                  command.applicationId(),
                  command.schemaVersion(),
                  fingerprint,
                  command.occurredAt(),
                  ApplicationCommandHandlerSupport.officeCode(command.applicationContent()),
                  command.status(),
                  command.laaReference(),
                  command.applicationContent(),
                  command.serialisedRequest(),
                  command.potentialDuplicates()))
          .forEach(eventAppender::append);
    } else {
      ApplicationDecider.decideStartDraft(
          application.getState(), command.schemaVersion(), fingerprint);
    }
    return command.applicationId();
  }
}

package uk.gov.justice.laa.dstew.access.command.application.handler;

import jakarta.annotation.Nullable;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetails;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetailsFactory;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

/** Handles creation of applications. */
@Component
public class CreateApplicationCommandHandler {

  /** Creates a new application or handles an idempotent retry. */
  @CommandHandler
  public UUID handle(
      CreateApplicationCommand command,
      @InjectEntity(idProperty = "applicationId") @Nullable ApplicationAggregate application,
      ApplicationCreationDetailsFactory factory,
      JsonSchemaValidator jsonSchemaValidator,
      EventAppender eventAppender) {
    jsonSchemaValidator.validate(
        command.applicationContent(), command.schemaName(), command.schemaVersion());
    if (application == null) {
      ApplicationCreationDetails details = factory.prepare(command);
      ApplicationDecider.decideCreate(
              new ApplicationState(),
              command.applicationId(),
              command.schemaVersion(),
              PayloadFingerprint.compute(details.serialisedRequest()),
              details)
          .forEach(eventAppender::append);
    } else {
      ApplicationDecider.decideCreate(
          application.getState(),
          command.applicationId(),
          command.schemaVersion(),
          PayloadFingerprint.compute(command.serialisedRequest()),
          null);
    }
    return command.applicationId();
  }
}

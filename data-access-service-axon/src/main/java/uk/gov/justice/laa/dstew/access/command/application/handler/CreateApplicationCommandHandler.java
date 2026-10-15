package uk.gov.justice.laa.dstew.access.command.application.handler;

import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetails;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetailsFactory;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

/** Handles creation of applications. */
@Component
public class CreateApplicationCommandHandler {

  /** Creates a new application or handles an idempotent retry. */
  @CommandHandler
  public UUID handle(
      CreateApplicationCommand command,
      @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
      ApplicationCreationDetailsFactory factory,
      ApplicationDataStore applicationDataStore,
      JsonSchemaValidator jsonSchemaValidator,
      EventAppender eventAppender) {
    jsonSchemaValidator.validate(
        command.applicationContent(), command.schemaName(), command.schemaVersion());
    if (application.getApplicationId() == null) {
      ApplicationCreationDetails details = factory.prepare(command);
      long applicationDataVersion = 0L;
      String fingerprint =
          applicationDataStore.append(command.applicationId(), applicationDataVersion, details);
      ApplicationDecider.decideCreate(
              application.getState(),
              command.applicationId(),
              command.schemaVersion(),
              fingerprint,
              details,
              applicationDataVersion)
          .forEach(eventAppender::append);
    } else {
      String fingerprint = ApplicationDataStore.fingerprint(command.serialisedRequest());
      ApplicationDecider.decideCreate(
          application.getState(),
          command.applicationId(),
          command.schemaVersion(),
          fingerprint,
          null,
          0L);
    }
    return command.applicationId();
  }
}

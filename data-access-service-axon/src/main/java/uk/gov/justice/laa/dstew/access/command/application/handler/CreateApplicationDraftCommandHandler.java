package uk.gov.justice.laa.dstew.access.command.application.handler;

import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.axonframework.modelling.repository.ManagedEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetailsFactory;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftStore;
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
      @InjectEntity(idProperty = "applicationId")
          ManagedEntity<UUID, ApplicationAggregate> managedApplication,
      ApplicationCreationDetailsFactory factory,
      ApplicationDraftStore draftStore,
      JsonSchemaValidator jsonSchemaValidator,
      EventAppender eventAppender) {
    jsonSchemaValidator.validate(
        command.applicationContent(), command.schemaName(), command.schemaVersion());
    ApplicationAggregate application = managedApplication.entity();
    if (application == null) {
      factory.validate(command.applicationContent());
      ApplicationDraftPayload payload =
          new ApplicationDraftPayload(
              command.status(),
              command.laaReference(),
              command.applicationContent(),
              command.serialisedRequest(),
              command.potentialDuplicates());
      String fingerprint =
          draftStore.upsert(
              command.applicationId(), payload, command.serialisedRequest(), command.occurredAt());
      ApplicationDecider.decideStartDraft(
              new ApplicationState(),
              command.applicationId(),
              command.schemaVersion(),
              fingerprint,
              command.occurredAt(),
              ApplicationCommandHandlerSupport.officeCode(command.applicationContent()))
          .forEach(eventAppender::append);
    } else {
      String fingerprint = PayloadFingerprint.compute(command.serialisedRequest());
      ApplicationDecider.decideStartDraft(
          application.getState(),
          command.applicationId(),
          command.schemaVersion(),
          fingerprint,
          command.occurredAt());
    }
    return command.applicationId();
  }
}

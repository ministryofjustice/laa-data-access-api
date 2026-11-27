package uk.gov.justice.laa.dstew.access.command.application.handler;

import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetailsFactory;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftStore;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftV2Command;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

/**
 * Submits v2 Application drafts. The stored content is validated against the contract first, then
 * the shared submission flow promotes it to the immutable application store.
 */
@Component
public class SubmitApplicationDraftV2CommandHandler {

  private static final String BASE_CIVIL_APPLICATION_SCHEMA = "BaseCivilApplication.json";

  private final SubmitApplicationDraftCommandHandler submitDelegate;
  private final JsonSchemaValidator jsonSchemaValidator;

  public SubmitApplicationDraftV2CommandHandler(
      SubmitApplicationDraftCommandHandler submitDelegate,
      JsonSchemaValidator jsonSchemaValidator) {
    this.submitDelegate = submitDelegate;
    this.jsonSchemaValidator = jsonSchemaValidator;
  }

  /** Validates the draft content and submits it, returning the submitted Application ID. */
  @CommandHandler
  public UUID handle(
      SubmitApplicationDraftV2Command command,
      @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
      ApplicationDraftStore draftStore,
      ApplicationCreationDetailsFactory detailsFactory,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {
    ApplicationDraftPayload draft =
        ApplicationCommandHandlerSupport.requireDraft(command.applicationId(), draftStore);
    jsonSchemaValidator.validate(
        draft.applicationContent(),
        BASE_CIVIL_APPLICATION_SCHEMA,
        application.getState().getSchemaVersion());
    return submitDelegate.handle(
        new SubmitApplicationDraftCommand(command.applicationId(), command.occurredAt()),
        application,
        draftStore,
        detailsFactory,
        applicationDataStore,
        eventAppender);
  }
}

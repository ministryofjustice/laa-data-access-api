package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import java.util.List;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationProvider;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDecider;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Handles creation of prior-authority drafts. */
@Component
public class CreatePriorAuthorityDraftCommandHandler {

  /** Creates a new prior-authority draft and emits the corresponding event. */
  @CommandHandler
  public void handle(
      CreatePriorAuthorityDraftCommand command,
      PriorAuthorityDraftStore draftStore,
      PriorAuthorityDataStore dataStore,
      @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {

    if (draftStore.exists(command.priorAuthorityId())
        || dataStore.exists(command.priorAuthorityId())) {
      throw new PriorAuthorityCreationConflictException(command.priorAuthorityId());
    }

    if (!application.isGranted()) {
      throw new ValidationException(
          List.of(
              "Prior authority requires the application to have an overall decision of GRANTED"));
    }

    String officeCode = applicationOfficeCode(applicationDataStore, command.applicationId());
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(
            command.priorAuthorityId(),
            command.applicationId(),
            command.content(),
            command.serialisedRequest(),
            command.occurredAt(),
            null,
            officeCode);

    draftStore.upsert(
        command.priorAuthorityId(),
        command.applicationId(),
        payload,
        command.serialisedRequest(),
        command.occurredAt());
    eventAppender.append(PriorAuthorityDecider.decideStartDraft(command, officeCode));
  }

  private static String applicationOfficeCode(
      ApplicationDataStore applicationDataStore, UUID applicationId) {
    long latestVersion = applicationDataStore.latestVersion(applicationId);
    ApplicationDataPayload applicationData = applicationDataStore.get(applicationId, latestVersion);
    ApplicationProvider provider = applicationData.provider();
    return provider == null ? null : provider.getOfficeCode();
  }
}

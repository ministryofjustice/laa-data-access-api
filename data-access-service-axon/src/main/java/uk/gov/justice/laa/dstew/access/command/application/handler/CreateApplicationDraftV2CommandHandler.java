package uk.gov.justice.laa.dstew.access.command.application.handler;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftStore;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftV2Command;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Creates v2 Application drafts. Business content is stored without contract validation. */
@Component
public class CreateApplicationDraftV2CommandHandler {

  /**
   * Creates a new draft or handles an idempotent retry, returning the current draft version. A
   * retry whose only change is envelope metadata updates the stored metadata.
   */
  @CommandHandler
  public long handle(
      CreateApplicationDraftV2Command command,
      @InjectEntity(idProperty = "applicationId") @Nullable ApplicationAggregate application,
      ApplicationDraftStore draftStore,
      EventAppender eventAppender) {
    String officeCode = ApplicationCommandHandlerSupport.officeCode(command.applicationContent());
    if (officeCode == null) {
      throw new ValidationException(List.of("provider.officeCode is required"));
    }
    if (application == null) {
      ApplicationDraftPayload payload =
          new ApplicationDraftPayload(
              command.status(),
              command.laaReference(),
              command.applicationContent(),
              command.serialisedRequest(),
              command.potentialDuplicates(),
              Map.of(),
              command.metadata());
      String fingerprint =
          draftStore.upsert(
              command.applicationId(), payload, command.serialisedRequest(), command.occurredAt());
      ApplicationDecider.decideStartDraft(
              new ApplicationState(),
              command.applicationId(),
              command.schemaVersion(),
              fingerprint,
              command.occurredAt(),
              officeCode)
          .forEach(eventAppender::append);
    } else {
      String fingerprint = PayloadFingerprint.compute(command.serialisedRequest());
      ApplicationDecider.decideStartDraft(
          application.getState(),
          command.applicationId(),
          command.schemaVersion(),
          fingerprint,
          command.occurredAt());
      ApplicationDraftPayload existing =
          ApplicationCommandHandlerSupport.requireDraft(command.applicationId(), draftStore);
      if (!existing.metadata().equals(command.metadata())) {
        draftStore.upsert(
            command.applicationId(),
            existing.withMetadata(command.metadata()),
            command.serialisedRequest(),
            command.occurredAt());
        eventAppender.append(
            ApplicationDecider.decideDraftUpdated(command.applicationId(), command.occurredAt()));
      }
    }
    return draftStore.findVersion(command.applicationId()).orElseThrow();
  }
}

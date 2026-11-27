package uk.gov.justice.laa.dstew.access.command.application.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftStore;
import uk.gov.justice.laa.dstew.access.command.application.draft.UpdateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCallerResource;
import uk.gov.justice.laa.dstew.access.security.OfficeCodeResourceWriteAccessResolver;
import uk.gov.justice.laa.dstew.access.security.RequireWriteAccess;
import uk.gov.justice.laa.dstew.access.security.WriteAccessResolverResource;

/** Replaces the body of an existing v2 Application draft without validating its content. */
@Component
public class UpdateApplicationDraftCommandHandler {

  /** Updates the draft body, keeps recorded document filenames, and returns the new version. */
  @CommandHandler
  @RequireWriteAccess(resolver = OfficeCodeResourceWriteAccessResolver.class)
  public long handle(
      @AuthenticatedCallerResource UpdateApplicationDraftCommand command,
      @InjectEntity(idProperty = "applicationId") @WriteAccessResolverResource
          ApplicationAggregate application,
      ApplicationDraftStore draftStore,
      EventAppender eventAppender) {
    ApplicationDraftPayload existing =
        ApplicationCommandHandlerSupport.requireDraft(command.applicationId(), draftStore);
    ApplicationDraftPayload payload =
        new ApplicationDraftPayload(
            command.status(),
            command.laaReference(),
            command.applicationContent(),
            command.serialisedRequest(),
            command.potentialDuplicates(),
            existing.documentFilenames(),
            command.metadata());
    draftStore.upsert(
        command.applicationId(), payload, command.serialisedRequest(), command.occurredAt());
    eventAppender.append(
        ApplicationDecider.decideDraftUpdated(command.applicationId(), command.occurredAt()));
    return draftStore.findVersion(command.applicationId()).orElseThrow();
  }
}

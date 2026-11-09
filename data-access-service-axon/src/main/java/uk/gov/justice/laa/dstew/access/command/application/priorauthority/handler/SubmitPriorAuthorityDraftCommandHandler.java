package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDecider;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.SubmitPriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCallerResource;
import uk.gov.justice.laa.dstew.access.security.OfficeCodeResourceWriteAccessResolver;
import uk.gov.justice.laa.dstew.access.security.RequireWriteAccess;
import uk.gov.justice.laa.dstew.access.security.WriteAccessResolverResource;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

/** Handles submission of prior-authority drafts. */
@Component
public class SubmitPriorAuthorityDraftCommandHandler {

  /** Submits a prior-authority draft, validates its schema, and emits the submitted event. */
  @CommandHandler
  @RequireWriteAccess(resolver = OfficeCodeResourceWriteAccessResolver.class)
  public void handle(
      @AuthenticatedCallerResource SubmitPriorAuthorityDraftCommand command,
      JsonSchemaValidator jsonSchemaValidator,
      @InjectEntity(idProperty = "priorAuthorityId") @WriteAccessResolverResource
          PriorAuthorityAggregate priorAuthority,
      EventAppender eventAppender) {

    PriorAuthorityDataPayload payload =
        priorAuthority
            .getState()
            .draft()
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Prior Authority draft not found: " + command.priorAuthorityId()));

    int schemaVersion = priorAuthority.getState().getSchemaVersion();

    jsonSchemaValidator.validate(payload.content(), "PriorAuthority.json", schemaVersion);

    eventAppender.append(PriorAuthorityDecider.decideSubmit(command, priorAuthority.getState()));
  }
}

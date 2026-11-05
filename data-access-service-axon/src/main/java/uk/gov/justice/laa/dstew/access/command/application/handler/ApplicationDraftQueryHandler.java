package uk.gov.justice.laa.dstew.access.command.application.handler;

import jakarta.annotation.Nullable;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftExistsQuery;

/** Answers draft questions by loading the Application aggregate. */
@Component
public class ApplicationDraftQueryHandler {

  /** Returns whether the Application has an open draft. */
  @QueryHandler
  public boolean handle(
      ApplicationDraftExistsQuery query,
      @InjectEntity(idProperty = "applicationId") @Nullable ApplicationAggregate application) {
    return ApplicationCommandHandlerSupport.findDraft(application).isPresent();
  }
}

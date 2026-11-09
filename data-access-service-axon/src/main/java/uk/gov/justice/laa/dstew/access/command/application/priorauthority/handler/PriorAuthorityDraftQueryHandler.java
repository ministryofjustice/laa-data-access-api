package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import jakarta.annotation.Nullable;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.FindPriorAuthorityDraftQuery;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftSummary;

/** Answers draft questions by loading the Prior Authority aggregate. */
@Component
public class PriorAuthorityDraftQueryHandler {

  /** Returns the open draft's summary, or {@link PriorAuthorityDraftSummary#NONE}. */
  @QueryHandler
  public PriorAuthorityDraftSummary handle(
      FindPriorAuthorityDraftQuery query,
      @InjectEntity(idProperty = "priorAuthorityId") @Nullable
          PriorAuthorityAggregate priorAuthority) {
    if (priorAuthority == null) {
      return PriorAuthorityDraftSummary.NONE;
    }
    return priorAuthority
        .getState()
        .draft()
        .map(
            draft ->
                new PriorAuthorityDraftSummary(draft.applicationId(), draft.documentFilenames()))
        .orElse(PriorAuthorityDraftSummary.NONE);
  }
}

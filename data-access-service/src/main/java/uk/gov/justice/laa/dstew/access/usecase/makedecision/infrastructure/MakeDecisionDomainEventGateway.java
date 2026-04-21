package uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.OverallDecisionStatus;

/** Thin adapter gateway for make-decision domain events. */
public interface MakeDecisionDomainEventGateway {
  void saveDecisionEvent(
      UUID applicationId,
      UUID caseworkerId,
      String serialisedRequest,
      String eventDescription,
      OverallDecisionStatus decision);
}

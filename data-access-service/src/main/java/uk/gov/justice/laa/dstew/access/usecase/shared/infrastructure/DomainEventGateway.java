package uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.OverallDecisionStatus;

/** Gateway interface for publishing domain events. */
public interface DomainEventGateway {
  void saveCreatedEvent(ApplicationDomain saved, String serialisedRequest);

  void saveDecisionEvent(
      UUID applicationId,
      UUID caseworkerId,
      String serialisedRequest,
      String eventDescription,
      OverallDecisionStatus decision);
}

package uk.gov.justice.laa.dstew.access.usecase.shared.infrastructure;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;

/** Gateway interface for publishing domain events. */
public interface DomainEventGateway {
  void saveCreatedEvent(ApplicationDomain saved, String serialisedRequest);

  void saveMakeDecisionEvent(
      UUID applicationId,
      UUID caseworkerId,
      String serialisedRequest,
      String eventDescription,
      String domainEventTypeName);
}

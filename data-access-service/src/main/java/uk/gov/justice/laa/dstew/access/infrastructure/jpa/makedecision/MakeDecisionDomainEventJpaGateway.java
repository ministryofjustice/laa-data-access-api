package uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.OverallDecisionStatus;
import uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.DomainEventJpaGateway;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.MakeDecisionDomainEventGateway;

/** Translates OverallDecisionStatus to DomainEventType and delegates to shared gateway. */
public class MakeDecisionDomainEventJpaGateway implements MakeDecisionDomainEventGateway {

  private final DomainEventJpaGateway sharedDomainEventGateway;

  public MakeDecisionDomainEventJpaGateway(DomainEventJpaGateway sharedDomainEventGateway) {
    this.sharedDomainEventGateway = sharedDomainEventGateway;
  }

  @Override
  public void saveDecisionEvent(
      UUID applicationId,
      UUID caseworkerId,
      String serialisedRequest,
      String eventDescription,
      OverallDecisionStatus decision) {
    DomainEventType type =
        decision == OverallDecisionStatus.GRANTED
            ? DomainEventType.APPLICATION_MAKE_DECISION_GRANTED
            : DomainEventType.APPLICATION_MAKE_DECISION_REFUSED;
    sharedDomainEventGateway.saveMakeDecisionEvent(
        applicationId, caseworkerId, serialisedRequest, eventDescription, type.name());
  }
}

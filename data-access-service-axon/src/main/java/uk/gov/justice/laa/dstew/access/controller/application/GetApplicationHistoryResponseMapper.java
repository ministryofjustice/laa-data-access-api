package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.ZoneOffset;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEventResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistoryResponse;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityEventResponse;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityHistoryGroup;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryEventResult;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryResult;
import uk.gov.justice.laa.dstew.access.query.application.history.PriorAuthorityHistoryEventResult;
import uk.gov.justice.laa.dstew.access.query.application.history.PriorAuthorityHistoryGroupResult;

/** Maps the Axon application-history projection to the shared HTTP response contract. */
@Component
public class GetApplicationHistoryResponseMapper {

  /** Maps history rows in their repository-provided chronological order. */
  public ApplicationHistoryResponse toResponse(ApplicationHistoryResult result) {
    var applicationEvents = result.applicationHistoryEvents().stream().map(this::toEvent).toList();
    var priorAuthorityGroups =
        result.priorAuthorityHistoryGroups().stream().map(this::toPaGroup).toList();
    return ApplicationHistoryResponse.builder()
        .events(applicationEvents)
        .priorAuthorities(priorAuthorityGroups)
        .build();
  }

  private PriorAuthorityHistoryGroup toPaGroup(PriorAuthorityHistoryGroupResult group) {
    return PriorAuthorityHistoryGroup.builder()
        .priorAuthorityId(group.priorAuthorityId())
        .priorAuthorityType(PriorAuthorityType.fromValue(group.priorAuthorityType()))
        .events(group.events().stream().map(this::toPaEvent).toList())
        .build();
  }

  private PriorAuthorityEventResponse toPaEvent(PriorAuthorityHistoryEventResult event) {
    return PriorAuthorityEventResponse.builder()
        .eventType(event.eventType())
        .createdAt(event.occurredAt().atOffset(ZoneOffset.UTC))
        .createdBy(event.serviceName() == null ? "UNKNOWN" : event.serviceName())
        .caseworkerId(event.caseworkerId())
        .eventDescription(event.eventDescription())
        .build();
  }

  private ApplicationDomainEventResponse toEvent(ApplicationHistoryEventResult event) {
    return ApplicationDomainEventResponse.builder()
        .applicationId(event.applicationId())
        .domainEventType(DomainEventType.fromValue(event.eventType()))
        .createdAt(event.occurredAt().atOffset(ZoneOffset.UTC))
        .createdBy(event.serviceName() == null ? "UNKNOWN" : event.serviceName())
        .caseworkerId(event.caseworkerId())
        .eventDescription(event.eventDescription())
        .build();
  }
}

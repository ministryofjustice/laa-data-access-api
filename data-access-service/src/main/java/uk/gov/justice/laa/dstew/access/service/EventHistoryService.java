package uk.gov.justice.laa.dstew.access.service;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEventResponse;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;

public interface EventHistoryService {

  List<ApplicationDomainEventResponse> getEvents(
      UUID applicationId, @Valid List<DomainEventType> eventType);
}

package uk.gov.justice.laa.dstew.access.service;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEvent;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;

public interface EventHistoryService {

  List<ApplicationDomainEvent> getEvents(UUID applicationId,
                                         @Valid List<DomainEventType> eventType);


}

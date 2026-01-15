package uk.gov.justice.laa.dstew.access.service;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEvent;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;

public interface EventHistoryService {

  /**
   * Provides a list of events associated with an application in createdAt ascending order.
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  List<ApplicationDomainEvent> getEvents(UUID applicationId,
                                         @Valid List<DomainEventType> eventType);


}

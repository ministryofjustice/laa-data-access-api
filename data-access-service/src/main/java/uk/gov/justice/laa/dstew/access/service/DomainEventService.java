package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.exception.DomainEventPublishException;
import uk.gov.justice.laa.dstew.access.mapper.DomainEventMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEvent;
import uk.gov.justice.laa.dstew.access.model.AssignApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.specification.DomainEventSpecification;

/**
 * Service class for managing domain events.
 */
@Service
@RequiredArgsConstructor
public class DomainEventService {
  private final DomainEventRepository domainEventRepository;
  private final ObjectMapper objectMapper;
  private final DomainEventMapper mapper;

  /**
   * posts a domain event {@link DomainEventEntity} object.
   *
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void saveAssignApplicationDomainEvent(
                    UUID applicationId,
                    UUID caseworkerId,
                    String eventDescription) {

    AssignApplicationDomainEventDetails domainEventDetails = AssignApplicationDomainEventDetails.builder()
            .applicationId(applicationId)
            .caseWorkerId(caseworkerId)
            .createdAt(Instant.now())
            .createdBy("")
            .eventDescription(eventDescription)
            .build();

    DomainEventEntity domainEventEntity = DomainEventEntity.builder()
                  .applicationId(applicationId)
                  .caseworkerId(caseworkerId)
                  .createdAt(Instant.now())
                  .createdBy("")
                  .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
                  .data(getEventDetailsAsJson(domainEventDetails))
                  .build();

    domainEventRepository.save(domainEventEntity);
  }

  /**
   * Converts domain event details to JSON string.
   *
   * @param domainEventDetails the domain event details
   * @return JSON string representation of the domain event details
   */
  private String getEventDetailsAsJson(AssignApplicationDomainEventDetails domainEventDetails) {
    try {
      return objectMapper.writeValueAsString(domainEventDetails);
    } catch (JsonProcessingException e) {
      throw new DomainEventPublishException(String.format("Unable to save Domain Event of type: %s",
              DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER.name()));
    }
  }

  /**
  * Provides a list of events associated with an application in createdAt ascending order.
  */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public List<ApplicationDomainEvent> getEvents(UUID applicationId,
      @Valid List<DomainEventType> eventType) {

    var filterEventType = DomainEventSpecification.filterEventTypes(eventType);
    Specification<DomainEventEntity> filter = DomainEventSpecification.filterApplicationId(applicationId)
                                                                      .and(filterEventType);

    Comparator<ApplicationDomainEvent> comparer = Comparator.comparing(ApplicationDomainEvent::getCreatedAt);
    return domainEventRepository.findAll(filter).stream().map(mapper::toDomainEvent).sorted(comparer).toList();
  }
}
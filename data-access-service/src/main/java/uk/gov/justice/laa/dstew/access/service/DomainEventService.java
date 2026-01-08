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
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.exception.DomainEventPublishException;
import uk.gov.justice.laa.dstew.access.mapper.DomainEventMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEvent;
import uk.gov.justice.laa.dstew.access.model.AssignApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.UnassignApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.UpdateApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.specification.DomainEventSpecification;

/**
 * Service class for managing domain events.
 */
@Service
@RequiredArgsConstructor
public class DomainEventService {

  private static final String defaultCreatedByName = "";

  private final DomainEventRepository domainEventRepository;
  private final ObjectMapper objectMapper;
  private final DomainEventMapper mapper;

  /**
   * Shared internal logic for persisting domain events.
   *
   */
  private void saveDomainEvent(
      UUID applicationId,
      UUID caseworkerId,
      DomainEventType eventType,
      Object data) {

    DomainEventEntity entity =
        DomainEventEntity.builder()
            .applicationId(applicationId)
            .caseworkerId(caseworkerId)
            .createdAt(Instant.now())
            .createdBy(defaultCreatedByName)
            .type(eventType)
            .data(getEventDetailsAsJson(data, eventType))
            .build();

    domainEventRepository.save(entity);
  }

  /**
   * Posts an APPLICATION_CREATED domain event.
   *
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void saveCreateApplicationDomainEvent(
      ApplicationEntity applicationEntity,
      String createdBy) {

    CreateApplicationDomainEventDetails domainEventDetails =
        CreateApplicationDomainEventDetails.builder()
            .applicationId(applicationEntity.getId())
            .createdDate(applicationEntity.getCreatedAt())
            .createdBy(applicationEntity.getCreatedBy())
            .applicationStatus(String.valueOf(applicationEntity.getStatus()))
            .applicationContent(applicationEntity.getApplicationContent().toString())
            .build();

    DomainEventEntity domainEventEntity =
          DomainEventEntity.builder()
              .applicationId(applicationEntity.getId())
              .caseworkerId(null)
              .type(DomainEventType.APPLICATION_CREATED)
              .createdAt(Instant.now())
              .createdBy(createdBy)
              .data(getEventDetailsAsJson(domainEventDetails, DomainEventType.APPLICATION_CREATED))
              .build();

    domainEventRepository.save(domainEventEntity);
  }

  /**
   * Posts an APPLICATION_UPDATED domain event.
   *
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void saveUpdateApplicationDomainEvent(
      ApplicationEntity applicationEntity,
      String updatedBy) {

    UpdateApplicationDomainEventDetails domainEventDetails =
        UpdateApplicationDomainEventDetails.builder()
            .applicationId(applicationEntity.getId())
            .updatedDate(applicationEntity.getModifiedAt())
            .updatedBy(updatedBy)
            .applicationStatus(String.valueOf(applicationEntity.getStatus()))
            .applicationContent(applicationEntity.getApplicationContent().toString())
            .build();

    saveDomainEvent(
        applicationEntity.getId(),
        applicationEntity.getCaseworker() != null ? applicationEntity.getCaseworker().getId() : null,
        DomainEventType.APPLICATION_UPDATED,
        domainEventDetails
    );
  }


  /**
   * Posts an ASSIGN_APPLICATION_TO_CASEWORKER domain event.
   *
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void saveAssignApplicationDomainEvent(
      UUID applicationId,
      UUID caseworkerId,
      String eventDescription) {

    AssignApplicationDomainEventDetails domainEventDetails =
        AssignApplicationDomainEventDetails.builder()
          .applicationId(applicationId)
          .caseWorkerId(caseworkerId)
          .createdAt(Instant.now())
          .createdBy(defaultCreatedByName)
          .eventDescription(eventDescription)
          .build();

    saveDomainEvent(
        applicationId,
        caseworkerId,
        DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER,
        domainEventDetails
    );
  }

  /**
   * Converts domain event details to JSON string.
   *
   * @param domainEventDetails the domain event details
   * @param domainEventType    domain event type enum
   * @return JSON string representation of the domain event details
   */
  private String getEventDetailsAsJson(Object domainEventDetails, DomainEventType domainEventType) {
    try {
      return objectMapper.writeValueAsString(domainEventDetails);
    } catch (JsonProcessingException e) {
      throw new DomainEventPublishException(String.format("Unable to save Domain Event of type: %s",
          domainEventType.name()));
    }
  }

  /**
   * Posts a domain event {@link DomainEventEntity} object.
   *
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void saveUnassignApplicationDomainEvent(
      UUID applicationId,
      UUID caseworkerId,
      String eventDescription) {

    UnassignApplicationDomainEventDetails eventDetails = UnassignApplicationDomainEventDetails.builder()
        .applicationId(applicationId)
        .caseworkerId(caseworkerId)
        .createdAt(Instant.now())
        .createdBy(defaultCreatedByName)
        .eventDescription(eventDescription)
        .build();

    saveDomainEvent(
        applicationId,
        caseworkerId,
        DomainEventType.UNASSIGN_APPLICATION_TO_CASEWORKER,
        eventDetails
    );
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
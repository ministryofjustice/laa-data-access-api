package uk.gov.justice.laa.dstew.access.service;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.config.ServiceNameContext;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.exception.DomainEventPublishException;
import uk.gov.justice.laa.dstew.access.mapper.DomainEventMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEvent;
import uk.gov.justice.laa.dstew.access.model.AssignApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.Event;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.UnassignApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.UpdateApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.specification.DomainEventSpecification;

/**
 * Service class for managing domain events.
 * Service name for all domain events is retrieved from the request-scoped ServiceNameContext,
 * which is populated by the ServiceNameInterceptor from the X-Service-Name header.
 */
@Service
@RequiredArgsConstructor
public class DomainEventService {

  private static final String defaultCreatedByName = "";

  private final DomainEventRepository domainEventRepository;
  private final ObjectMapper objectMapper;
  private final EventHistoryPublisher eventHistoryPublisher;
  @Value("${aws.event.history.enabled:false}")
  private boolean awsEventHistoryEnabled;
  private final DomainEventMapper mapper;
  private final ServiceNameContext serviceNameContext;

  /**
   * Shared internal logic for persisting domain events.
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
            .serviceName(serviceNameContext.getServiceName())
            .build();

    if (awsEventHistoryEnabled) {
      eventHistoryPublisher.processEventAsync(Event.convertToEvent(entity));
    }
    domainEventRepository.save(entity);

  }

  /**
   * Posts an APPLICATION_CREATED domain event.
   */
  @AllowApiCaseworker
  public void saveCreateApplicationDomainEvent(
      ApplicationEntity applicationEntity,
      ApplicationCreateRequest request,
      String createdBy) {

    CreateApplicationDomainEventDetails domainEventDetails =
        CreateApplicationDomainEventDetails.builder()
            .applicationId(applicationEntity.getId())
            .createdDate(applicationEntity.getCreatedAt())
            .laaReference(applicationEntity.getLaaReference())
            .applicationStatus(String.valueOf(applicationEntity.getStatus()))
            .request(getEventDetailsAsJson(request, DomainEventType.APPLICATION_CREATED))
            .build();

    DomainEventEntity domainEventEntity =
        DomainEventEntity.builder()
            .applicationId(applicationEntity.getId())
            .caseworkerId(null)
            .type(DomainEventType.APPLICATION_CREATED)
            .createdAt(Instant.now())
            .createdBy(createdBy)
            .data(getEventDetailsAsJson(domainEventDetails, DomainEventType.APPLICATION_CREATED))
            .serviceName(serviceNameContext.getServiceName())
            .build();

    if (awsEventHistoryEnabled) {
      eventHistoryPublisher.processEventAsync(Event.convertToEvent(domainEventEntity));
    }
    domainEventRepository.save(domainEventEntity);


  }

  /**
   * Posts an APPLICATION_UPDATED domain event.
   */
  @AllowApiCaseworker
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
   */
  @AllowApiCaseworker
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
    } catch (JacksonException e) {
      throw new DomainEventPublishException(String.format("Unable to save Domain Event of type: %s",
          domainEventType.name()));
    }
  }

  /**
   * Posts an UNASSIGN_APPLICATION_TO_CASEWORKER domain event.
   */
  @AllowApiCaseworker
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
  @AllowApiCaseworker
  public List<ApplicationDomainEvent> getEvents(UUID applicationId,
                                                @Valid List<DomainEventType> eventType) {

    var filterEventType = DomainEventSpecification.filterEventTypes(eventType);
    Specification<DomainEventEntity> filter = DomainEventSpecification.filterApplicationId(applicationId)
        .and(filterEventType);

    Comparator<ApplicationDomainEvent> comparer = Comparator.comparing(ApplicationDomainEvent::getCreatedAt);
    return domainEventRepository.findAll(filter).stream().map(mapper::toDomainEvent).sorted(comparer).toList();
  }


  /**
   * Posts a make decision domain event.
   *
   * @param applicationId the id of the application for which the decision was made
   * @param request       the details of the decision that was made
   * @param caseworkerId  the id of the caseworker who made the decision
   * @param domainEventType the type of the domain event to post,
   *                        either APPLICATION_MAKE_DECISION_REFUSED or APPLICATION_MAKE_DECISION_GRANTED
   */
  @AllowApiCaseworker
  public void saveMakeDecisionDomainEvent(
      UUID applicationId,
      MakeDecisionRequest request, UUID caseworkerId, DomainEventType domainEventType) {

    String eventDescription = request.getEventHistory().getEventDescription();

    MakeDecisionDomainEventDetails domainEventDetails =
        MakeDecisionDomainEventDetails.builder()
            .applicationId(applicationId)
            .caseworkerId(caseworkerId)
            .createdAt(Instant.now())
            .request(getEventDetailsAsJson(request, domainEventType))
            .eventDescription(eventDescription)
            .build();

    saveDomainEvent(
        applicationId,
        caseworkerId,
        domainEventType,
        domainEventDetails
    );
  }
}

package uk.gov.justice.laa.dstew.access.service.domainevents;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.config.ServiceNameContext;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.exception.DomainEventPublishException;
import uk.gov.justice.laa.dstew.access.model.AssignApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationNoteDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.UnassignApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.UpdateApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/**
 * Service class for managing domain events. Service name for all domain events is retrieved from
 * the request-scoped ServiceNameContext, which is populated by the ServiceNameInterceptor from the
 * X-Service-Name header.
 *
 * <p>TODO: caseworkerId currently sourced from the assigned caseworker on the application. Once
 * authentication is in place, all domain events should use the logged-in user from the auth token
 * instead. Some functions excluded from code coverage as the functionality is due for refactor.
 */
@Service
@RequiredArgsConstructor
public class SaveDomainEventService {

  private static final String defaultCreatedByName = "";

  private final DomainEventRepository domainEventRepository;
  private final ObjectMapper objectMapper;
  private final ServiceNameContext serviceNameContext;

  /** Shared internal logic for persisting domain events. */
  private void saveDomainEvent(
      UUID applicationId, UUID caseworkerId, DomainEventType eventType, Object data) {

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

    domainEventRepository.save(entity);
  }

  /** Posts an APPLICATION_CREATED domain event using domain types (new clean-architecture path). */
  public void saveCreateApplicationDomainEvent(ApplicationDomain domain, String serialisedRequest) {

    CreateApplicationDomainEventDetails domainEventDetails =
        CreateApplicationDomainEventDetails.builder()
            .applicationId(domain.id())
            .createdDate(domain.createdAt())
            .laaReference(domain.laaReference())
            .applicationStatus(domain.status())
            .request(serialisedRequest)
            .build();

    DomainEventEntity domainEventEntity =
        DomainEventEntity.builder()
            .applicationId(domain.id())
            .caseworkerId(null)
            .type(DomainEventType.APPLICATION_CREATED)
            .createdAt(Instant.now())
            .createdBy(null)
            .data(getEventDetailsAsJson(domainEventDetails, DomainEventType.APPLICATION_CREATED))
            .serviceName(serviceNameContext.getServiceName())
            .build();

    domainEventRepository.save(domainEventEntity);
  }

  /**
   * Posts an APPLICATION_UPDATED domain event. excluded from code coverage as caseworker will be
   * removed once RBAC is sorted. Domain event is asserted in UpdateApplicationTest
   */
  @ExcludeFromGeneratedCodeCoverage
  public void saveUpdateApplicationDomainEvent(
      ApplicationEntity applicationEntity, String updatedBy) {

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
        applicationEntity.getCaseworker() != null
            ? applicationEntity.getCaseworker().getId()
            : null,
        DomainEventType.APPLICATION_UPDATED,
        domainEventDetails);
  }

  /** Posts an ASSIGN_APPLICATION_TO_CASEWORKER domain event. */
  public void saveAssignApplicationDomainEvent(
      UUID applicationId, UUID caseworkerId, String eventDescription) {

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
        domainEventDetails);
  }

  /**
   * Converts domain event details to JSON string.
   *
   * @param domainEventDetails the domain event details
   * @param domainEventType domain event type enum
   * @return JSON string representation of the domain event details
   */
  @ExcludeFromGeneratedCodeCoverage
  private String getEventDetailsAsJson(Object domainEventDetails, DomainEventType domainEventType) {
    try {
      return objectMapper.writeValueAsString(domainEventDetails);
    } catch (JacksonException e) {
      throw new DomainEventPublishException(
          String.format("Unable to save Domain Event of type: %s", domainEventType.name()));
    }
  }

  /** Posts an UNASSIGN_APPLICATION_TO_CASEWORKER domain event. */
  public void saveUnassignApplicationDomainEvent(
      UUID applicationId, UUID caseworkerId, String eventDescription) {

    UnassignApplicationDomainEventDetails eventDetails =
        UnassignApplicationDomainEventDetails.builder()
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
        eventDetails);
  }

  /**
   * Posts a make decision domain event using domain types (new clean-architecture path). The event
   * type is derived from {@code overallDecision}: {@code "GRANTED"} maps to {@link
   * DomainEventType#APPLICATION_MAKE_DECISION_GRANTED}, any other value maps to {@link
   * DomainEventType#APPLICATION_MAKE_DECISION_REFUSED}.
   *
   * @param applicationId the id of the application for which the decision was made
   * @param serialisedRequest the pre-serialised JSON of the make-decision request
   * @param caseworkerId the id of the caseworker who made the decision
   * @param overallDecision the overall decision string ({@code "GRANTED"} or {@code "REFUSED"})
   * @param eventDescription the event description from the request's eventHistory
   */
  @AllowApiCaseworker
  public void saveMakeDecisionDomainEvent(
      UUID applicationId,
      String serialisedRequest,
      UUID caseworkerId,
      String overallDecision,
      String eventDescription) {

    DomainEventType domainEventType =
        "GRANTED".equals(overallDecision)
            ? DomainEventType.APPLICATION_MAKE_DECISION_GRANTED
            : DomainEventType.APPLICATION_MAKE_DECISION_REFUSED;

    MakeDecisionDomainEventDetails domainEventDetails =
        MakeDecisionDomainEventDetails.builder()
            .applicationId(applicationId)
            .caseworkerId(caseworkerId)
            .createdAt(Instant.now())
            .request(serialisedRequest)
            .eventDescription(eventDescription)
            .build();

    saveDomainEvent(applicationId, caseworkerId, domainEventType, domainEventDetails);
  }

  /**
   * Posts an APPLICATION_NOTES domain event using domain types (new clean-architecture path).
   *
   * @param applicationId the UUID of the application
   * @param caseworkerId the UUID of the assigned caseworker, or {@code null} if unassigned
   * @param serialisedNoteRequest the pre-serialised JSON of the original {@code CreateNoteRequest}
   */
  public void saveCreateApplicationNoteDomainEvent(
      UUID applicationId, UUID caseworkerId, String serialisedNoteRequest) {

    DomainEventType domainEventType = DomainEventType.APPLICATION_NOTES;

    CreateApplicationNoteDomainEventDetails domainEventDetails =
        CreateApplicationNoteDomainEventDetails.builder()
            .applicationId(applicationId)
            .caseworkerId(caseworkerId)
            .request(serialisedNoteRequest)
            .createdDate(Instant.now())
            .build();

    saveDomainEvent(applicationId, caseworkerId, domainEventType, domainEventDetails);
  }
}

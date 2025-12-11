package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.DomainEventEntity;
import uk.gov.justice.laa.dstew.access.exception.DomainEventPublishException;
import uk.gov.justice.laa.dstew.access.model.AssignApplicationDomainEventDetails;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;

/**
 * Service class for managing domain events.
 */
@Service
public class DomainEventService {
  private final DomainEventRepository domainEventRepository;
  private final ObjectMapper objectMapper;

  /**
   * Constructs a new {@link DomainEventService} with the required repository and mapper.
   *
   * @param domainEventRepository the repository used to access domain event data.
   */
  public DomainEventService(
          final DomainEventRepository domainEventRepository,
          final ObjectMapper objectMapper) {
    this.domainEventRepository = domainEventRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * posts a domain event {@link DomainEventEntity} object.
   *
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void saveAssignApplicationDomainEvent(
                    UUID applicationId,
                    UUID caseworkerId,
                    String eventDescription) {

    AssignApplicationDomainEventDetails data = AssignApplicationDomainEventDetails.builder()
            .applicationId(applicationId)
            .caseWorkerId(caseworkerId)
            .createdAt(Instant.now())
            .createdBy("")
            .eventDescription(eventDescription)
            .build();

      DomainEventEntity domainEventEntity = null;
      try {
          domainEventEntity = DomainEventEntity.builder()
                  .applicationId(applicationId)
                  .caseWorkerId(caseworkerId)
                  .createdAt(Instant.now())
                  .createdBy("")
                  .type(DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER)
                  .data(objectMapper.writeValueAsString(data))
                  .build();
      } catch (JsonProcessingException e) {
          throw new DomainEventPublishException(String.format("Unable to save Domain Event of type: %s",
                  DomainEventType.ASSIGN_APPLICATION_TO_CASEWORKER.name()));
      }
      domainEventRepository.save(domainEventEntity);
  }
}
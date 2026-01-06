package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.dstew.access.exception.CaseworkerNotFoundException;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.EventHistory;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.validation.ApplicationValidations;

/**
 * Service class for managing Applications.
 */
@Service
public class ApplicationService {

  private final int applicationVersion = 1;
  private final ApplicationRepository applicationRepository;
  private final ApplicationMapper applicationMapper;
  private final ApplicationValidations applicationValidations;
  private final ObjectMapper objectMapper;
  private final CaseworkerRepository caseworkerRepository;
  private final DomainEventService domainEventService;
  private final Javers javers;

  /**
   * Constructs an ApplicationService with required dependencies.
   *
   * @param applicationRepository the repository
   * @param applicationMapper the mapper between entity and DTO
   * @param applicationValidations validations for requests
   * @param objectMapper Jackson ObjectMapper for JSONB
   */
  public ApplicationService(final ApplicationRepository applicationRepository,
                            final ApplicationMapper applicationMapper,
                            final ApplicationValidations applicationValidations,
                            final ObjectMapper objectMapper,
                            final CaseworkerRepository caseworkerRepository,
                            final DomainEventService domainEventService) {
    this.applicationRepository = applicationRepository;
    this.applicationMapper = applicationMapper;
    this.applicationValidations = applicationValidations;
    this.javers = JaversBuilder.javers().build();
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    this.objectMapper = objectMapper;
    this.caseworkerRepository = caseworkerRepository;
    this.domainEventService = domainEventService;
  }

  /**
   * Retrieve a single application by ID.
   *
   * @param id application UUID
   * @return application DTO
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public Application getApplication(final UUID id) {
    final ApplicationEntity entity = checkIfApplicationExists(id);
    return applicationMapper.toApplication(entity);
  }

  /**
   * Create a new application.
   *
   * @param req DTO containing creation fields
   * @return UUID of the created application
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public UUID createApplication(final ApplicationCreateRequest req) {
    applicationValidations.checkApplicationCreateRequest(req);
    final ApplicationEntity entity = applicationMapper.toApplicationEntity(req);
    entity.setSchemaVersion(applicationVersion);
    final ApplicationEntity saved = applicationRepository.save(entity);

    createAndSendHistoricRecord(saved, null);

    return saved.getId();
  }

  /**
   * Update an existing application.
   *
   * @param id application UUID
   * @param req DTO with update fields
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void updateApplication(final UUID id, final ApplicationUpdateRequest req) {
    final ApplicationEntity entity = checkIfApplicationExists(id);
    applicationValidations.checkApplicationUpdateRequest(req, entity);
    applicationMapper.updateApplicationEntity(entity, req);
    entity.setModifiedAt(Instant.now());
    applicationRepository.save(entity);

    // Optional: create snapshot for audit/history
    objectMapper.convertValue(
        applicationMapper.toApplication(entity),
        new TypeReference<Map<String, Object>>() {}
    );
  }

  /**
   * Placeholder for historic/audit record creation.
   *
   * @param entity application entity
   * @param actionType optional action type
   */
  protected void createAndSendHistoricRecord(final ApplicationEntity entity, final Object actionType) {
    // Implement audit/history publishing if required
  }

  /**
   * Check existence of an application by ID.
   *
   * @param id UUID of application
   * @return found entity
   */
  private ApplicationEntity checkIfApplicationExists(final UUID id) {
    return applicationRepository.findById(id)
        .orElseThrow(() -> new ApplicationNotFoundException(
            String.format("No application found with id: %s", id)
        ));
  }

  /**
   * Checks that applications exist for all the IDs provided.
   *
   * @param ids Collection of UUIDs of applications
   * @return found entity
   */
  private List<ApplicationEntity> checkIfAllApplicationsExist(@NonNull final List<UUID> ids) {
    var idsToFetch = ids.stream().distinct().toList();
    var applications = applicationRepository.findAllById(idsToFetch);
    List<UUID> fetchedApplicationsIds = applications.stream().map(app -> app.getId()).toList();
    String missingIds = idsToFetch.stream()
                                  .filter(appId -> !fetchedApplicationsIds.contains(appId))
                                  .map(appId -> appId.toString())
                                  .collect(Collectors.joining(","));
    if (!missingIds.isEmpty()) {
      String exceptionMsg = "No application found with ids: " + missingIds;
      throw new ApplicationNotFoundException(exceptionMsg);
    }
    return applications;
  }

  /**
   * Assigns a caseworker to an application.
   *
   * @param caseworkerId the UUID of the caseworker to assign
   * @param applicationIds the UUIDs of the applications to assign the caseworker to
   * @throws ApplicationNotFoundException   if the application does not exist
   * @throws CaseworkerNotFoundException    if the caseworker does not exist
   */
  @Transactional
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void assignCaseworker(@NonNull final UUID caseworkerId,
                               final List<UUID> applicationIds,
                               final EventHistory eventHistory) {
    final CaseworkerEntity caseworker = caseworkerRepository.findById(caseworkerId)
        .orElseThrow(() -> new CaseworkerNotFoundException(
            String.format("No caseworker found with id: %s", caseworkerId)));

    final List<ApplicationEntity> applications = checkIfAllApplicationsExist(applicationIds);

    applications.forEach(app -> {

      if (!applicationCurrentCaseworkerIsCaseworker(app, caseworker)) {
        app.setCaseworker(caseworker);
        app.setModifiedAt(Instant.now());
        applicationRepository.save(app);
      }

      domainEventService.saveAssignApplicationDomainEvent(
              app.getId(),
              caseworker.getId(),
              eventHistory.getEventDescription());
    });

  }

  /**
   * Unassigns a caseworker from an application.
   *
   * @param applicationId the UUID of the application to update
   * @throws ApplicationNotFoundException   if the application does not exist
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void unassignCaseworker(final UUID applicationId, EventHistory history) {
    final ApplicationEntity entity = applicationRepository.findById(applicationId)
        .orElseThrow(() -> new ApplicationNotFoundException(
            String.format("No application found with id: %s", applicationId)
        ));

    if (entity.getCaseworker() == null) {
      return;
    }

    entity.setCaseworker(null);
    entity.setModifiedAt(Instant.now());

    applicationRepository.save(entity);

    domainEventService.saveUnassignApplicationDomainEvent(
            entity.getId(),
            null,
            history.getEventDescription());

  }

  /**
   * Check if an application has a caseworker assigned already and checks if the
   * assigned caseworker matches the given caseworker.
   *
   */
  private static boolean applicationCurrentCaseworkerIsCaseworker(ApplicationEntity application, CaseworkerEntity caseworker) {
    return application.getCaseworker() != null
            && application.getCaseworker().equals(caseworker);
  }
}

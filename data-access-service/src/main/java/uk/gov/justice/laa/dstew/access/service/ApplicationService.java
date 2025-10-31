package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.validation.ApplicationValidations;

/**
 * Service class for managing Applications.
 */
@Service
public class ApplicationService {

  private final ApplicationRepository applicationRepository;
  private final ApplicationMapper applicationMapper;
  private final ApplicationValidations applicationValidations;
  private final ObjectMapper objectMapper;
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
                            final ObjectMapper objectMapper) {
    this.applicationRepository = applicationRepository;
    this.applicationMapper = applicationMapper;
    this.applicationValidations = applicationValidations;
    this.javers = JaversBuilder.javers().build();
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    this.objectMapper = objectMapper;
  }

  /**
   * Retrieve all applications.
   *
   * @return list of applications
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public List<Application> getAllApplications() {
    return applicationRepository.findAll().stream()
        .map(applicationMapper::toApplication)
        .toList();
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
  protected ApplicationEntity checkIfApplicationExists(final UUID id) {
    return applicationRepository.findById(id)
        .orElseThrow(() -> new ApplicationNotFoundException(
            String.format("No application found with id: %s", id)
        ));
  }

  /**
   * Populate target object with field values from a map.
   *
   * @param target object to populate
   * @param fieldValues map of field names to values
   */
  private void populateFields(final Object target, final Map<String, Object> fieldValues) {
    fieldValues.forEach((name, value) -> {
      try {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
      } catch (NoSuchFieldException | IllegalAccessException ignored) {
        // Ignore unknown or inaccessible fields
      }
    });
  }
}

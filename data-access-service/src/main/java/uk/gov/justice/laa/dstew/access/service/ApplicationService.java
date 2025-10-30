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
 * Service layer providing CRUD operations and validation for {@link Application} entities.
 * Handles create, read, and update operations with validation and transformation logic.
 */
@Service
public class ApplicationService {

  private final ApplicationRepository applicationRepository;
  private final ApplicationMapper applicationMapper;
  private final ApplicationValidations applicationValidations;
  private final ObjectMapper objectMapper;
  private final Javers javers;

  /**
   * Constructs an {@link ApplicationService} with the required dependencies.
   *
   * @param applicationRepository the repository to manage persistence
   * @param applicationMapper the mapper to convert between entities and models
   * @param applicationValidations the validator for application business rules
   * @param objectMapper the Jackson object mapper
   */
  public ApplicationService(
      final ApplicationRepository applicationRepository,
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
   * Retrieves all applications from the repository.
   *
   * @return a list of all {@link Application} records
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public List<Application> getAllApplications() {
    return applicationRepository.findAll().stream()
        .map(applicationMapper::toApplication)
        .toList();
  }

  /**
   * Retrieves an application by its ID.
   *
   * @param id the UUID of the application
   * @return the corresponding {@link Application}
   * @throws ApplicationNotFoundException if no application is found
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public Application getApplication(UUID id) {
    var applicationEntity = checkIfApplicationExists(id);
    return applicationMapper.toApplication(applicationEntity);
  }

  /**
   * Creates a new application in the system.
   *
   * @param applicationCreateReq the request body containing the new application data
   * @return the UUID of the newly created application
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public UUID createApplication(ApplicationCreateRequest applicationCreateReq) {
    applicationValidations.checkApplicationCreateRequest(applicationCreateReq);

    var applicationEntity = applicationMapper.toApplicationEntity(applicationCreateReq);
    var savedEntity = applicationRepository.save(applicationEntity);

    createAndSendHistoricRecord(savedEntity, null);
    return savedEntity.getId();
  }

  /**
   * Updates an existing application by ID.
   *
   * @param id the ID of the application to update
   * @param applicationUpdateReq the update request containing new values
   * @throws ApplicationNotFoundException if no application with the given ID exists
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void updateApplication(UUID id, ApplicationUpdateRequest applicationUpdateReq) {
    var applicationEntity = checkIfApplicationExists(id);
    applicationValidations.checkApplicationUpdateRequest(applicationUpdateReq, applicationEntity);

    applicationMapper.updateApplicationEntity(applicationEntity, applicationUpdateReq);
    applicationRepository.save(applicationEntity);

    objectMapper.convertValue(
        applicationMapper.toApplication(applicationEntity),
        new TypeReference<Map<String, Object>>() {}
    );
  }

  /**
   * Creates and sends a historical record of an application change (currently a stub).
   *
   * @param applicationEntity the application entity
   * @param actionType the action type that triggered the record
   */
  protected void createAndSendHistoricRecord(ApplicationEntity applicationEntity, Object actionType) {
    // Implementation placeholder (no Checkstyle empty-catch issues)
  }

  /**
   * Checks if an application with the given ID exists.
   *
   * @param id the application ID
   * @return the found {@link ApplicationEntity}
   * @throws ApplicationNotFoundException if not found
   */
  protected ApplicationEntity checkIfApplicationExists(UUID id) {
    return applicationRepository.findById(id)
        .orElseThrow(() -> new ApplicationNotFoundException(
            String.format("No application found with id: %s", id)));
  }

  /**
   * Retrieves the "id" field value from an arbitrary object using reflection.
   *
   * @param object the target object
   * @return the value of its "id" field, or null if inaccessible
   */
  private Object getObjectId(Object object) {
    try {
      Field idField = object.getClass().getDeclaredField("id");
      idField.setAccessible(true);
      return idField.get(object);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      System.out.printf("Unable to access id field for object of type %s%n", object.getClass().getName());
      return null;
    }
  }

  /**
   * Populates the fields of an object dynamically using reflection.
   *
   * @param target the target object to populate
   * @param fieldValues a map of field names to values
   */
  private void populateFields(Object target, Map<String, Object> fieldValues) {
    fieldValues.forEach((name, value) -> {
      try {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
      } catch (NoSuchFieldException | IllegalAccessException e) {
        System.out.printf("Skipping unknown or inaccessible field: %s%n", name);
      }
    });
  }
}

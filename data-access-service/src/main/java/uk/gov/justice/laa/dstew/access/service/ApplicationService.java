package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.javers.core.diff.changetype.ValueChange;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.config.SqsProducer;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceeding;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.specification.ApplicationSpecification;
import uk.gov.justice.laa.dstew.access.validation.ApplicationValidations;

/**
 * Service class for handling items requests.
 */
@Service
public class ApplicationService {

  private final ApplicationRepository applicationRepository;

  private final ApplicationMapper applicationMapper;

  private final ApplicationValidations applicationValidations;

  private final SqsProducer sqsProducer;

  private final ObjectMapper objectMapper;

  private final Javers javers;

  /**
   * Create a service for applications for legal aid.
   *
   * @param applicationRepository the repository of such applications.
   * @param applicationMapper the mapper between entity and DTOs.
   * @param applicationValidations the validation methods for request DTOs.
   * @param sqsProducer the sender of messages to the queue.
   * @param objectMapper JSON mapper to serialize the history.
   */
  public ApplicationService(
          final ApplicationRepository applicationRepository,
          final ApplicationMapper applicationMapper,
          final ApplicationValidations applicationValidations,
          final SqsProducer sqsProducer,
          final ObjectMapper objectMapper) {
    this.applicationRepository = applicationRepository;
    this.applicationMapper = applicationMapper;
    this.applicationValidations = applicationValidations;
    this.sqsProducer = sqsProducer;
    this.javers = JaversBuilder.javers().build();

    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    this.objectMapper = objectMapper;
  }

  /**
   * Gets all applications.
   *
   * @return the list of applications
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public List<Application> getAllApplications() {
    return applicationRepository.findAll(ApplicationSpecification.isPending()).stream().map(applicationMapper::toApplication).toList();
  }

  /**
   * Gets an application for a given id.
   *
   * @param id the application id
   * @return the requested application
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationReader')")
  public Application getApplication(UUID id) {
    var applicationEntity = checkIfApplicationExists(id);
    return applicationMapper.toApplication(applicationEntity);
  }

  /**
   * Creates an application.
   *
   * @param applicationCreateReq the application to be created
   * @return the id of the created application
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public UUID createApplication(ApplicationCreateRequest applicationCreateReq) {
    applicationValidations.checkApplicationCreateRequest(applicationCreateReq);

    var applicationEntity = applicationMapper.toApplicationEntity(applicationCreateReq);

    //set the application entity id to null to ensure a new entity is created
    if (applicationEntity.getProceedings() != null) {
      applicationEntity.getProceedings().forEach(proceeding -> {
        proceeding.setApplication(applicationEntity);
      });
    }

    var savedEntity = applicationRepository.save(applicationEntity);

    // create history message for the created application
    createAndSendHistoricRecord(savedEntity, null);

    return savedEntity.getId();
  }

  /**
   * Update an application for legal aid, keeping history.
   *
   * @param id the unique identifier of the application.
   * @param applicationUpdateReq the DTO containing the change.
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void updateApplication(UUID id, ApplicationUpdateRequest applicationUpdateReq) {
    var applicationEntity = checkIfApplicationExists(id);

    applicationValidations.checkApplicationUpdateRequest(applicationUpdateReq, applicationEntity);

    applicationMapper.updateApplicationEntity(applicationEntity, applicationUpdateReq);
    if (applicationEntity.getProceedings() != null) {
      applicationEntity.getProceedings().forEach(p -> p.setApplication(applicationEntity));
    }

    applicationRepository.save(applicationEntity);

    var snapshot = objectMapper
            .convertValue(applicationMapper.toApplication(applicationEntity),
                    new TypeReference<Map<String, Object>>() {});
  }

  protected void createAndSendHistoricRecord(ApplicationEntity applicationEntity, Object actionType) {
  }

  protected ApplicationEntity checkIfApplicationExists(UUID id) {
    return applicationRepository
            .findById(id)
            .orElseThrow(
                    () -> new ApplicationNotFoundException(String.format("No application found with id: %s", id)));
  }

  private Object getObjectId(Object object) {
    try {
      Field idField = object.getClass().getDeclaredField("id");
      idField.setAccessible(true);
      return idField.get(object);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      return null;
    }
  }

  private void populateFields(Object target, Map<String, Object> fieldValues) {
    fieldValues.forEach((name, value) -> {
      try {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
      } catch (NoSuchFieldException | IllegalAccessException e) {
        // Optionally log or ignore unknown fields
      }
    });
  }

}
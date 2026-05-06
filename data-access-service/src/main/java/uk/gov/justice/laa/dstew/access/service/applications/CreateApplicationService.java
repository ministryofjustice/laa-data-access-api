package uk.gov.justice.laa.dstew.access.service.applications;

import jakarta.transaction.Transactional;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.LinkedApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.mapper.ProceedingMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.LinkedApplication;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.LinkedApplicationRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.validation.ApplicationValidations;
import uk.gov.justice.laa.dstew.access.validation.PayloadValidationService;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/**
 * Service for creating applications. Handles validation, mapping, and persistence of new
 * applications, as well as linking to lead applications and saving related proceedings.
 */
@Service
@RequiredArgsConstructor
public class CreateApplicationService {

  private final int applicationVersion = 1;
  private final ApplicationRepository applicationRepository;
  private final ApplicationMapper applicationMapper;
  private final ProceedingMapper proceedingMapper;
  private final ApplicationValidations applicationValidations;
  private final SaveDomainEventService saveDomainEventService;
  private final ApplicationContentParserService applicationContentParser;
  private final PayloadValidationService payloadValidationService;
  private final LinkedApplicationRepository linkedApplicationRepository;

  /**
   * Create a new application.
   *
   * @param req DTO containing creation fields
   * @return UUID of the created application
   */
  @AllowApiCaseworker
  @Transactional
  public UUID createApplication(final ApplicationCreateRequest req) {
    ApplicationEntity entity = applicationMapper.toApplicationEntity(req);
    ApplicationContent applicationContent =
        payloadValidationService.convertAndValidate(
            req.getApplicationContent(), ApplicationContent.class);
    setValuesFromApplicationContent(entity, applicationContent);
    checkForDuplicateApplication(entity.getApplyApplicationId());
    entity.setSchemaVersion(applicationVersion);

    Set<ProceedingEntity> proceedingEntities = buildProceedingEntities(applicationContent);
    if (!proceedingEntities.isEmpty()) {
      entity.setProceedings(proceedingEntities);
    }

    final ApplicationEntity saved = applicationRepository.save(entity);

    linkToLeadApplicationIfApplicable(applicationContent, saved);
    saveDomainEventService.saveCreateApplicationDomainEvent(saved, req, null);
    createAndSendHistoricRecord(saved, null);

    return saved.getId();
  }

  /**
   * Builds ProceedingEntity objects from ApplicationContent without setting applicationId (managed
   * by JPA via @JoinColumn on ApplicationEntity.proceedings).
   */
  private Set<ProceedingEntity> buildProceedingEntities(ApplicationContent applicationContent) {
    if (applicationContent.getProceedings() == null
        || applicationContent.getProceedings().isEmpty()) {
      return Set.of();
    }
    return applicationContent.getProceedings().stream()
        .map(p -> proceedingMapper.toProceedingEntity(p))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Sets key fields in the application entity based on parsed application content.
   *
   * @param entity application entity to update
   * @param requestAppContent application content from the request
   */
  private void setValuesFromApplicationContent(
      ApplicationEntity entity, ApplicationContent requestAppContent) {

    var parsedContentDetails =
        applicationContentParser.normaliseApplicationContentDetails(requestAppContent);
    entity.setApplyApplicationId(parsedContentDetails.applyApplicationId());
    entity.setUsedDelegatedFunctions(parsedContentDetails.usedDelegatedFunctions());
    entity.setCategoryOfLaw(parsedContentDetails.categoryOfLaw());
    entity.setMatterType(parsedContentDetails.matterType());
    entity.setSubmittedAt(parsedContentDetails.submittedAt());
    entity.setOfficeCode(parsedContentDetails.officeCode());
  }

  private void linkToLeadApplicationIfApplicable(
      ApplicationContent appContent, ApplicationEntity entityToAdd) {
    final Optional<ApplicationEntity> leadApplication = getLeadApplication(appContent);
    leadApplication.ifPresent(
        leadApp -> {
          var link =
              LinkedApplicationEntity.builder()
                  .leadApplicationId(leadApp.getId())
                  .associatedApplicationId(entityToAdd.getId())
                  .build();
          linkedApplicationRepository.save(link);
        });
  }

  private Optional<ApplicationEntity> getLeadApplication(ApplicationContent requestContent) {
    final UUID leadApplicationId = getLeadApplicationId(requestContent.getAllLinkedApplications());
    if (requestContent.getAllLinkedApplications() != null) {
      List<UUID> list =
          requestContent.getAllLinkedApplications().stream()
              .map(LinkedApplication::getAssociatedApplicationId)
              .filter(uuid -> !uuid.equals(requestContent.getId()))
              .toList();

      checkIfAllAssociatedApplicationsExist(list);
    }

    if (leadApplicationId == null) {
      return Optional.empty();
    }

    var leadApplication = applicationRepository.findByApplyApplicationId(leadApplicationId);
    if (leadApplication == null) {
      throw new ResourceNotFoundException(
          "Linking failed > Lead application not found, ID: " + leadApplicationId);
    }

    return Optional.of(leadApplication);
  }

  private static UUID getLeadApplicationId(List<LinkedApplication> linkedApplications) {
    return (linkedApplications != null && !linkedApplications.isEmpty())
        ? linkedApplications.getFirst().getLeadApplicationId()
        : null;
  }

  /**
   * Checks that applications exist for all the IDs provided.
   *
   * @param associatedApplyIds Collection of apply applications ids
   */
  private void checkIfAllAssociatedApplicationsExist(final List<UUID> associatedApplyIds) {
    applicationValidations.checkApplicationIdList(associatedApplyIds);
    List<UUID> foundApplyAppIds =
        applicationRepository.findAllByApplyApplicationIdIn(associatedApplyIds).stream()
            .map(ApplicationEntity::getApplyApplicationId)
            .toList();
    if (foundApplyAppIds.size() != associatedApplyIds.size()) {
      List<UUID> remainingIds =
          associatedApplyIds.stream().filter(id -> !foundApplyAppIds.contains(id)).toList();
      String exceptionMsg =
          "No linked application found with associated apply ids: " + remainingIds;
      throw new ResourceNotFoundException(exceptionMsg);
    }
  }

  /**
   * Validates that the application ID is not a duplicate.
   *
   * @param applyApplicationId the UUID of the application to check
   * @throws ValidationException if a duplicate application exists
   */
  private void checkForDuplicateApplication(final UUID applyApplicationId) {
    if (applicationRepository.existsByApplyApplicationId(applyApplicationId)) {
      throw new ValidationException(
          List.of("Application already exists for Apply Application Id: " + applyApplicationId));
    }
  }

  /**
   * Placeholder for historic/audit record creation.
   *
   * @param entity application entity
   * @param actionType optional action type
   */
  protected void createAndSendHistoricRecord(
      final ApplicationEntity entity, final Object actionType) {
    // Implement audit/history publishing if required
  }
}

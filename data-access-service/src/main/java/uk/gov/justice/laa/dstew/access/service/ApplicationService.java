package uk.gov.justice.laa.dstew.access.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.EventHistory;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.model.RequestApplicationContent;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.repository.DecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.MeritsDecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
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
  private final ApplicationContentParserService applicationContentParser;
  private final DecisionRepository decisionRepository;
  private final ProceedingRepository proceedingRepository;
  private final MeritsDecisionRepository meritsDecisionRepository;
  private final ProceedingsService proceedingsService;

  /**
   * Constructs an ApplicationService with required dependencies.
   *
   * @param applicationRepository  the repository
   * @param applicationMapper      the mapper between entity and DTO
   * @param applicationValidations validations for requests
   * @param objectMapper           Jackson ObjectMapper for JSONB
   */
  public ApplicationService(final ApplicationRepository applicationRepository,
                            final ApplicationMapper applicationMapper,
                            final ApplicationValidations applicationValidations,
                            final ObjectMapper objectMapper,
                            final CaseworkerRepository caseworkerRepository,
                            final DecisionRepository decisionRepository,
                            final DomainEventService domainEventService,
                            final ApplicationContentParserService applicationContentParserService,
                            final ProceedingRepository proceedingRepository,
                            final MeritsDecisionRepository meritsDecisionRepository,
                            final ProceedingsService proceedingsService) {
    this.applicationRepository = applicationRepository;
    this.applicationMapper = applicationMapper;
    this.applicationValidations = applicationValidations;
    this.applicationContentParser = applicationContentParserService;
    this.proceedingRepository = proceedingRepository;
    this.proceedingsService = proceedingsService;
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    this.objectMapper = objectMapper;
    this.caseworkerRepository = caseworkerRepository;
    this.domainEventService = domainEventService;
    this.decisionRepository = decisionRepository;
    this.meritsDecisionRepository = meritsDecisionRepository;
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
    ApplicationEntity entity = applicationMapper.toApplicationEntity(req);
    RequestApplicationContent requestApplicationContent =
        objectMapper.convertValue(req.getApplicationContent(), RequestApplicationContent.class);
    setValuesFromApplicationContent(entity, requestApplicationContent);
    entity.setSchemaVersion(applicationVersion);

    final ApplicationEntity saved = applicationRepository.save(entity);

    
    proceedingsService.saveProceedings(requestApplicationContent.getApplicationContent(), saved.getId());
    domainEventService.saveCreateApplicationDomainEvent(saved, null);
    createAndSendHistoricRecord(saved, null);

    return saved.getId();
  }

  /**
   * Sets key fields in the application entity based on parsed application content.
   *
   * @param entity application entity to update
   * @param requestAppContent application content from the request
   */
  private void setValuesFromApplicationContent(ApplicationEntity entity,
                                               RequestApplicationContent requestAppContent) {


    var parsedContentDetails = applicationContentParser.normaliseApplicationContentDetails(requestAppContent);
    entity.setApplyApplicationId(parsedContentDetails.applyApplicationId());
    entity.setUsedDelegatedFunctions(parsedContentDetails.usedDelegatedFunctions());
    entity.setCategoryOfLaw(parsedContentDetails.categoryOfLaw());
    entity.setMatterType(parsedContentDetails.matterType());
    entity.setSubmittedAt(parsedContentDetails.submittedAt());
  }


  /**
   * Update an existing application.
   *
   * @param id  application UUID
   * @param req DTO with update fields
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void updateApplication(final UUID id, final ApplicationUpdateRequest req) {
    final ApplicationEntity entity = checkIfApplicationExists(id);
    applicationValidations.checkApplicationUpdateRequest(req);
    applicationMapper.updateApplicationEntity(entity, req);
    entity.setModifiedAt(Instant.now());
    applicationRepository.save(entity);

    domainEventService.saveUpdateApplicationDomainEvent(entity, null);

    // Optional: create snapshot for audit/history
    objectMapper.convertValue(
        applicationMapper.toApplication(entity),
        new TypeReference<Map<String, Object>>() {
        }
    );
  }

  /**
   * Placeholder for historic/audit record creation.
   *
   * @param entity     application entity
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
        .orElseThrow(() -> new ResourceNotFoundException(
            String.format("No application found with id: %s", id)
        ));
  }

  /**
   * Check existence of a caseworker by ID.
   *
   * @param caseworkerId userid of caseworker
   * @return found entity
   */
  private CaseworkerEntity checkIfCaseworkerExists(final UUID caseworkerId) {
    return caseworkerRepository.findById(caseworkerId)
            .orElseThrow(() -> new ResourceNotFoundException(
                    String.format("No caseworker found with id: %s", caseworkerId)));
  }

  /**
   * Checks that applications exist for all the IDs provided.
   *
   * @param ids Collection of UUIDs of applications
   * @return found entity
   */
  private List<ApplicationEntity> checkIfAllApplicationsExist(final List<UUID> ids) {
    applicationValidations.checkApplicationIdList(ids);
    var idsToFetch = ids.stream().distinct().toList();
    var applications = applicationRepository.findAllById(idsToFetch);
    List<UUID> fetchedApplicationsIds = applications.stream().map(ApplicationEntity::getId).toList();
    String missingIds = idsToFetch.stream()
        .filter(appId -> !fetchedApplicationsIds.contains(appId))
        .map(UUID::toString)
        .collect(Collectors.joining(","));
    if (!missingIds.isEmpty()) {
      String exceptionMsg = "No application found with ids: " + missingIds;
      throw new ResourceNotFoundException(exceptionMsg);
    }
    return applications;
  }

  /**
   * Assigns a caseworker to an application.
   *
   * @param caseworkerId   the UUID of the caseworker to assign
   * @param applicationIds the UUIDs of the applications to assign the caseworker to
   * @throws ResourceNotFoundException if the application or caseworker does not exist
   */
  @Transactional
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void assignCaseworker(@NonNull final UUID caseworkerId,
                               final List<UUID> applicationIds,
                               final EventHistory eventHistory) {
    final CaseworkerEntity caseworker = checkIfCaseworkerExists(caseworkerId);

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
   * @throws ResourceNotFoundException if the application does not exist
   */
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void unassignCaseworker(final UUID applicationId, EventHistory history) {
    final ApplicationEntity entity = checkIfApplicationExists(applicationId);

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

  /**
   * Update an existing application to add the decision details.
   *
   * @param applicationId application UUID
   * @param request DTO with update fields
   */
  @Transactional
  @PreAuthorize("@entra.hasAppRole('ApplicationWriter')")
  public void makeDecision(final UUID applicationId, final MakeDecisionRequest request) {
    final ApplicationEntity application = checkIfApplicationExists(applicationId);
    checkIfCaseworkerExists(request.getUserId());

    applicationValidations.checkApplicationMakeDecisionRequest(request);

    application.setStatus(request.getApplicationStatus());
    application.setModifiedAt(Instant.now());
    applicationRepository.save(application);

    DecisionEntity decision = decisionRepository.findByApplicationId(applicationId)
            .orElse(DecisionEntity.builder()
                    .applicationId(applicationId)
                    .meritsDecisions(Set.of())
                    .build());

    Set<MeritsDecisionEntity> merits = new LinkedHashSet<>(decision.getMeritsDecisions());

    request.getProceedings().forEach(proceeding -> {

      ProceedingEntity proceedingEntity = proceedingRepository.findById(proceeding.getProceedingId())
              .orElseThrow(
                      () -> new ResourceNotFoundException(
                              String.format("No proceeding found with id: %s", proceeding.getProceedingId()))
              );

      MeritsDecisionEntity meritDecisionEntity = decision.getMeritsDecisions().stream()
              .filter(m -> m.getProceeding().getId().equals(proceeding.getProceedingId()))
              .findFirst()
              .orElseGet(() -> {
                MeritsDecisionEntity newEntity = new MeritsDecisionEntity();
                newEntity.setProceeding(proceedingEntity);
                return newEntity;
              });

      meritDecisionEntity.setModifiedAt(Instant.now());
      meritDecisionEntity.setDecision(MeritsDecisionStatus.valueOf(proceeding.getMeritsDecision().getDecision().toString()));
      meritDecisionEntity.setReason(proceeding.getMeritsDecision().getRefusal().getReason());
      meritDecisionEntity.setJustification(proceeding.getMeritsDecision().getRefusal().getJustification());
      meritsDecisionRepository.save(meritDecisionEntity);
      merits.add(meritDecisionEntity);

    });

    decision.setMeritsDecisions(merits);
    decision.setOverallDecision(DecisionStatus.valueOf(request.getOverallDecision().getValue()));
    decision.setModifiedAt(Instant.now());
    decisionRepository.save(decision);

    if (decision.getOverallDecision() == DecisionStatus.REFUSED) {
      domainEventService.saveMakeDecisionRefusedDomainEvent(
              applicationId,
              request
      );
    }
  }
}

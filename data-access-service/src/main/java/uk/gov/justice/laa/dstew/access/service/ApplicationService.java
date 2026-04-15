package uk.gov.justice.laa.dstew.access.service;

import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.NoteEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.mapper.ApplicationMapper;
import uk.gov.justice.laa.dstew.access.mapper.MapperUtil;
import uk.gov.justice.laa.dstew.access.mapper.NoteMapper;
import uk.gov.justice.laa.dstew.access.mapper.ProceedingMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationMerits;
import uk.gov.justice.laa.dstew.access.model.ApplicationNoteResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationNotesResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceedingResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.repository.NoteRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.validation.ApplicationValidations;

/** Service class for managing Applications. */
@RequiredArgsConstructor
@Service
public class ApplicationService {

  private final ApplicationRepository applicationRepository;
  private final ApplicationMapper applicationMapper;
  private final ProceedingMapper proceedingMapper;
  private final ApplicationValidations applicationValidations;
  private final ObjectMapper objectMapper;
  private final CaseworkerRepository caseworkerRepository;
  private final DomainEventService domainEventService;
  private final ProceedingRepository proceedingRepository;
  private final NoteRepository noteRepository;
  private final NoteMapper noteMapper;

  /**
   * Retrieve a single application by ID.
   *
   * @param id application UUID
   * @return application DTO
   */
  @AllowApiCaseworker
  public ApplicationResponse getApplication(final UUID id) {
    final ApplicationEntity entity = checkIfApplicationExists(id);
    ApplicationResponse application = applicationMapper.toApplication(entity);

    Set<ProceedingEntity> proceedings = proceedingRepository.findAllByApplicationId(id);

    if (proceedings != null) {

      proceedings.forEach(
          proceeding -> {
            ApplicationProceedingResponse applicationProceedingResponse =
                proceedingMapper.toApplicationProceeding(proceeding);

            // List<Map<String, Object>> involvedChildren = getInvolvedChildren(entity);
            // if (involvedChildren != null) {
            //   List<Object> children = new ArrayList<>();
            //   involvedChildren.forEach(children::add);
            //   applicationProceeding.setInvolvedChildren(children);
            // }
            // else {
            //   applicationProceeding.setInvolvedChildren(null);
            // }

            if (entity.getDecision() != null) {
              Optional<MeritsDecisionEntity> meritsDecision =
                  entity.getDecision().getMeritsDecisions().stream()
                      .filter(m -> m.getProceeding().getId() == proceeding.getId())
                      .findFirst();

              meritsDecision.ifPresent(
                  meritsDecisionEntity ->
                      applicationProceedingResponse.setMeritsDecision(
                          meritsDecisionEntity.getDecision()));
            }
            application.getProceedings().add(applicationProceedingResponse);
          });
    }

    return application;
  }

  private List<Map<String, Object>> getInvolvedChildren(ApplicationEntity entity) {

    ApplicationContent applicationContent =
        MapperUtil.getObjectMapper()
            .convertValue(entity.getApplicationContent(), ApplicationContent.class);
    ApplicationMerits meritsObj = applicationContent.getApplicationMerits();

    if (meritsObj == null) {
      return null;
    }

    return meritsObj.getInvolvedChildren();
  }

  /**
   * Update an existing application.
   *
   * @param id application UUID
   * @param req DTO with update fields
   */
  @AllowApiCaseworker
  public void updateApplication(final UUID id, final ApplicationUpdateRequest req) {
    final ApplicationEntity entity = checkIfApplicationExists(id);
    applicationValidations.checkApplicationUpdateRequest(req);
    applicationMapper.updateApplicationEntity(entity, req);
    entity.setModifiedAt(Instant.now());
    applicationRepository.save(entity);

    domainEventService.saveUpdateApplicationDomainEvent(entity, null);

    // Optional: create snapshot for audit/history
    objectMapper.convertValue(
        applicationMapper.toApplication(entity), new TypeReference<Map<String, Object>>() {});
  }

  /**
   * Create a note for an application.
   *
   * @param id UUID of the application
   * @param request note to be created
   */
  @AllowApiCaseworker
  @Transactional
  public void createApplicationNote(final UUID id, final CreateNoteRequest request) {
    ApplicationEntity application = checkIfApplicationExists(id);
    noteRepository.save(NoteEntity.builder().applicationId(id).notes(request.getNotes()).build());
    domainEventService.saveCreateApplicationNoteDomainEvent(application, request);
  }

  /**
   * Retrieve all notes for an application in ascending date order.
   *
   * @param id UUID of the application
   * @return response containing list of notes in ascending createdAt order
   */
  @AllowApiCaseworker
  public ApplicationNotesResponse getApplicationNotes(final UUID id) {
    checkIfApplicationExists(id);
    List<ApplicationNoteResponse> notes =
        noteRepository.findByApplicationIdOrderByCreatedAtAsc(id).stream()
            .map(noteMapper::toApplicationNoteResponse)
            .toList();
    return new ApplicationNotesResponse().notes(notes);
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

  /**
   * Check existence of an application by ID.
   *
   * @param id UUID of application
   * @return found entity
   */
  private ApplicationEntity checkIfApplicationExists(final UUID id) {
    return applicationRepository
        .findById(id)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    String.format("No application found with id: %s", id)));
  }

  /**
   * Check existence of a caseworker by ID.
   *
   * @param caseworkerId userid of caseworker
   * @return found entity
   */
  private CaseworkerEntity checkIfCaseworkerExists(final UUID caseworkerId) {
    return caseworkerRepository
        .findById(caseworkerId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
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
    List<UUID> fetchedApplicationsIds =
        applications.stream().map(ApplicationEntity::getId).toList();
    String missingIds =
        idsToFetch.stream()
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
   * @param caseworkerId the UUID of the caseworker to assign
   * @param applicationIds the UUIDs of the applications to assign the caseworker to
   * @throws ResourceNotFoundException if the application or caseworker does not exist
   */
  @Transactional
  @AllowApiCaseworker
  public void assignCaseworker(
      @NonNull final UUID caseworkerId,
      final List<UUID> applicationIds,
      final EventHistoryRequest eventHistoryRequest) {
    final CaseworkerEntity caseworker = checkIfCaseworkerExists(caseworkerId);

    final List<ApplicationEntity> applications = checkIfAllApplicationsExist(applicationIds);

    applications.forEach(
        app -> {
          if (!applicationCurrentCaseworkerIsCaseworker(app, caseworker)) {
            app.setCaseworker(caseworker);
            app.setModifiedAt(Instant.now());
            applicationRepository.save(app);
          }

          domainEventService.saveAssignApplicationDomainEvent(
              app.getId(), caseworker.getId(), eventHistoryRequest.getEventDescription());
        });
  }

  /**
   * Unassigns a caseworker from an application.
   *
   * @param applicationId the UUID of the application to update
   * @throws ResourceNotFoundException if the application does not exist
   */
  @AllowApiCaseworker
  public void unassignCaseworker(final UUID applicationId, EventHistoryRequest history) {
    final ApplicationEntity entity = checkIfApplicationExists(applicationId);

    if (entity.getCaseworker() == null) {
      return;
    }

    entity.setCaseworker(null);
    entity.setModifiedAt(Instant.now());

    applicationRepository.save(entity);

    domainEventService.saveUnassignApplicationDomainEvent(
        entity.getId(), null, history.getEventDescription());
  }

  /**
   * Check if an application has a caseworker assigned already and checks if the assigned caseworker
   * matches the given caseworker.
   */
  private static boolean applicationCurrentCaseworkerIsCaseworker(
      ApplicationEntity application, CaseworkerEntity caseworker) {
    return application.getCaseworker() != null && application.getCaseworker().equals(caseworker);
  }

}

package uk.gov.justice.laa.dstew.access.service.applications;

import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CertificateRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.utils.ApplicationServiceHelper;
import uk.gov.justice.laa.dstew.access.utils.VersionCheckHelper;
import uk.gov.justice.laa.dstew.access.validation.ApplicationValidations;

/** Service for make decision process. */
@RequiredArgsConstructor
@Service
public class MakeDecisionService {

  private final ApplicationRepository applicationRepository;
  private final ApplicationValidations applicationValidations;
  private final SaveDomainEventService saveDomainEventService;
  private final CertificateRepository certificateRepository;
  private final ProceedingRepository proceedingRepository;

  /**
   * Update an existing application to add the decision details.
   *
   * @param applicationId application UUID
   * @param request DTO with update fields
   */
  @Transactional
  @AllowApiCaseworker
  public void makeDecision(final UUID applicationId, final MakeDecisionRequest request) {
    final ApplicationEntity application =
        ApplicationServiceHelper.getExistingApplication(applicationId, applicationRepository);
    VersionCheckHelper.checkEntityVersionLocking(
        applicationId, application.getVersion(), request.getApplicationVersion());
    final UUID caseworkerId = getCaseworkerId(application);
    applicationValidations.checkApplicationMakeDecisionRequest(request);

    // Validate proceedings belong to this application using in-memory set
    Map<UUID, ProceedingEntity> proceedingEntityMap =
        validateAndMapProceedingsFromAggregate(application, request);

    // Build/update DecisionEntity
    DecisionEntity decision =
        application.getDecision() != null
            ? application.getDecision()
            : DecisionEntity.builder().build();
    decision.setOverallDecision(DecisionStatus.valueOf(request.getOverallDecision().getValue()));
    decision.setModifiedAt(Instant.now());

    // Update meritsDecision on each proceeding
    request
        .getProceedings()
        .forEach(
            proceedingReq -> {
              ProceedingEntity proceedingEntity =
                  proceedingEntityMap.get(proceedingReq.getProceedingId());
              MeritsDecisionEntity meritsDecision =
                  proceedingEntity.getMeritsDecision() != null
                      ? proceedingEntity.getMeritsDecision()
                      : new MeritsDecisionEntity();
              meritsDecision.setDecision(
                  MeritsDecisionStatus.valueOf(
                      proceedingReq.getMeritsDecision().getDecision().toString()));
              meritsDecision.setReason(proceedingReq.getMeritsDecision().getReason());
              meritsDecision.setJustification(proceedingReq.getMeritsDecision().getJustification());
              meritsDecision.setModifiedAt(Instant.now());
              proceedingEntity.setMeritsDecision(meritsDecision);
            });

    application.setDecision(decision);
    application.setModifiedAt(Instant.now());
    application.setIsAutoGranted(request.getAutoGranted());

    // Persist or remove certificate based on overallDecision
    switch (decision.getOverallDecision()) {
      case GRANTED -> {
        if (request.getCertificate() != null) {
          CertificateEntity certificate =
              certificateRepository
                  .findByApplicationId(applicationId)
                  .map(
                      existing -> {
                        existing.setCertificateContent(request.getCertificate());
                        return existing;
                      })
                  .orElseGet(
                      () ->
                          CertificateEntity.builder()
                              .applicationId(applicationId)
                              .certificateContent(request.getCertificate())
                              .build());
          certificateRepository.save(certificate);
        }
      }
      case REFUSED -> {
        if (certificateRepository.existsByApplicationId(applicationId)) {
          certificateRepository.deleteByApplicationId(applicationId);
        }
      }
      default ->
          throw new IllegalStateException("Unexpected value: " + decision.getOverallDecision());
    }

    applicationRepository.save(application);

    switch (decision.getOverallDecision()) {
      case GRANTED ->
          saveDomainEventService.saveMakeDecisionDomainEvent(
              applicationId,
              request,
              caseworkerId,
              DomainEventType.APPLICATION_MAKE_DECISION_GRANTED);
      case REFUSED ->
          saveDomainEventService.saveMakeDecisionDomainEvent(
              applicationId,
              request,
              caseworkerId,
              DomainEventType.APPLICATION_MAKE_DECISION_REFUSED);
      default ->
          throw new IllegalStateException("Unexpected value: " + decision.getOverallDecision());
    }
  }

  /**
   * Validates that all proceedings in the request belong to the given application. First checks
   * proceedings linked to the application; for any not found there, checks whether they exist in
   * the database to produce appropriate error messages.
   *
   * @return a map of proceeding ID to entity for all requested proceedings
   */
  private Map<UUID, ProceedingEntity> validateAndMapProceedingsFromAggregate(
      ApplicationEntity application, MakeDecisionRequest request) {
    List<UUID> idsToFetch =
        request.getProceedings().stream()
            .map(MakeDecisionProceedingRequest::getProceedingId)
            .distinct()
            .toList();

    Map<UUID, ProceedingEntity> linkedMap =
        application.getProceedings() == null
            ? Map.of()
            : application.getProceedings().stream()
                .collect(Collectors.toMap(ProceedingEntity::getId, p -> p));

    // IDs not linked to this application
    List<UUID> notLinkedIds = idsToFetch.stream().filter(id -> !linkedMap.containsKey(id)).toList();

    List<String> errors = new ArrayList<>();

    if (!notLinkedIds.isEmpty()) {
      // Check which of the not-linked IDs actually exist in the database
      List<ProceedingEntity> foundInDb = proceedingRepository.findAllById(notLinkedIds);
      List<UUID> foundInDbIds = foundInDb.stream().map(ProceedingEntity::getId).toList();

      String proceedingIdsNotFound =
          notLinkedIds.stream()
              .filter(id -> !foundInDbIds.contains(id))
              .map(UUID::toString)
              .collect(Collectors.joining(","));

      String proceedingIdsNotLinkedToApplication =
          foundInDbIds.stream().map(UUID::toString).collect(Collectors.joining(","));

      if (!proceedingIdsNotFound.isEmpty()) {
        errors.add("No proceeding found with id: " + proceedingIdsNotFound);
      }
      if (!proceedingIdsNotLinkedToApplication.isEmpty()) {
        errors.add("Not linked to application: " + proceedingIdsNotLinkedToApplication);
      }

      if (!errors.isEmpty()) {
        throw new ResourceNotFoundException(String.join("; ", errors));
      }
    }

    return linkedMap;
  }

  /**
   * Gets the caseworker id from an application.
   *
   * @param application application entity
   * @return caseworker id
   */
  private static UUID getCaseworkerId(ApplicationEntity application) {
    final CaseworkerEntity caseworker = application.getCaseworker();
    return caseworker != null ? caseworker.getId() : null;
  }
}

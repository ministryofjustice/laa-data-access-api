package uk.gov.justice.laa.dstew.access.service.applications;

import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.NonNull;
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
import uk.gov.justice.laa.dstew.access.repository.DecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.MeritsDecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.domainEvents.SaveDomainEventService;
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
  private final DecisionRepository decisionRepository;
  private final ProceedingRepository proceedingRepository;
  private final MeritsDecisionRepository meritsDecisionRepository;
  private final CertificateRepository certificateRepository;

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

    application.setModifiedAt(Instant.now());
    application.setIsAutoGranted(request.getAutoGranted());
    applicationRepository.save(application);

    DecisionEntity decision =
        application.getDecision() != null
            ? application.getDecision()
            : DecisionEntity.builder().meritsDecisions(Set.of()).build();

    Set<MeritsDecisionEntity> merits = new LinkedHashSet<>(decision.getMeritsDecisions());

    Map<UUID, ProceedingEntity> proceedingEntityMap =
        getUuidProceedingEntityMap(applicationId, request);

    request
        .getProceedings()
        .forEach(
            proceeding -> {
              ProceedingEntity proceedingEntity =
                  proceedingEntityMap.get(proceeding.getProceedingId());
              saveMeritsDecisionEntity(proceeding, decision, proceedingEntity, merits);
            });

    decision.setMeritsDecisions(merits);
    decision.setOverallDecision(DecisionStatus.valueOf(request.getOverallDecision().getValue()));
    decision.setModifiedAt(Instant.now());
    decisionRepository.save(decision);

    // Persist certificate if overallDecision is GRANTED
    if (decision.getOverallDecision() == DecisionStatus.GRANTED
        && request.getCertificate() != null) {
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

    if (decision.getOverallDecision() == DecisionStatus.REFUSED) {
      if (certificateRepository.existsByApplicationId(applicationId)) {
        certificateRepository.deleteByApplicationId(applicationId);
      }
    }

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

    if (application.getDecision() == null) {
      application.setDecision(decision);
      applicationRepository.save(application);
    }
  }

  private @NonNull Map<UUID, ProceedingEntity> getUuidProceedingEntityMap(
      UUID applicationId, MakeDecisionRequest request) {
    List<UUID> proceedingIds =
        request.getProceedings().stream()
            .map(MakeDecisionProceedingRequest::getProceedingId)
            .toList();
    List<ProceedingEntity> proceedingEntities =
        checkIfAllProceedingsExistForApplication(applicationId, proceedingIds);
    return proceedingEntities.stream()
        .collect(Collectors.toMap(ProceedingEntity::getId, proceeding -> proceeding));
  }

  /**
   * Checks that all provided proceeding IDs exist and are linked to the specified application.
   * Throws a {@link ResourceNotFoundException} if any proceeding does not exist or is not linked to
   * the given application.
   *
   * @param applicationId the UUID of the application to check proceedings against
   * @param proceedingIds the list of proceeding UUIDs to validate
   * @return a list of {@link ProceedingEntity} objects corresponding to the provided IDs
   * @throws ResourceNotFoundException if any proceeding is missing or not linked to the application
   */
  private List<ProceedingEntity> checkIfAllProceedingsExistForApplication(
      final UUID applicationId, final List<UUID> proceedingIds) {
    List<UUID> idsToFetch = proceedingIds.stream().distinct().toList();
    List<ProceedingEntity> proceedings = proceedingRepository.findAllById(idsToFetch);

    List<UUID> foundProceedingIds = proceedings.stream().map(ProceedingEntity::getId).toList();

    String proceedingIdsNotFound =
        idsToFetch.stream()
            .filter(id -> !foundProceedingIds.contains(id))
            .map(UUID::toString)
            .collect(Collectors.joining(","));

    String proceedingIdsNotLinkedToApplication =
        proceedings.stream()
            .filter(p -> !p.getApplicationId().equals(applicationId))
            .map(p -> p.getId().toString())
            .collect(Collectors.joining(","));

    if (!proceedingIdsNotFound.isEmpty() || !proceedingIdsNotLinkedToApplication.isEmpty()) {
      List<String> errors = new ArrayList<>();
      if (!proceedingIdsNotFound.isEmpty()) {
        errors.add("No proceeding found with id: " + proceedingIdsNotFound);
      }
      if (!proceedingIdsNotLinkedToApplication.isEmpty()) {
        errors.add("Not linked to application: " + proceedingIdsNotLinkedToApplication);
      }
      throw new ResourceNotFoundException(String.join("; ", errors));
    }
    return proceedings;
  }

  private void saveMeritsDecisionEntity(
      MakeDecisionProceedingRequest proceeding,
      DecisionEntity decision,
      ProceedingEntity proceedingEntity,
      Set<MeritsDecisionEntity> merits) {
    MeritsDecisionEntity meritDecisionEntity =
        decision.getMeritsDecisions().stream()
            .filter(m -> m.getProceeding().getId().equals(proceeding.getProceedingId()))
            .findFirst()
            .orElseGet(
                () -> {
                  MeritsDecisionEntity newEntity = new MeritsDecisionEntity();
                  newEntity.setProceeding(proceedingEntity);
                  return newEntity;
                });

    meritDecisionEntity.setModifiedAt(Instant.now());
    meritDecisionEntity.setDecision(
        MeritsDecisionStatus.valueOf(proceeding.getMeritsDecision().getDecision().toString()));
    meritDecisionEntity.setReason(proceeding.getMeritsDecision().getReason());
    meritDecisionEntity.setJustification(proceeding.getMeritsDecision().getJustification());
    meritsDecisionRepository.save(meritDecisionEntity);
    merits.add(meritDecisionEntity);
  }

  /**
   * Gets the caseworker id from an application.
   *
   * @param application application entity
   * @return caseworker id
   */
  private static UUID getCaseworkerId(ApplicationEntity application) {
    final CaseworkerEntity caseworker = application.getCaseworker();
    // This logic will be implemented in the next iteration when security is implemented in the
    // service
    //    if (caseworker == null) {
    //      throw new ResourceNotFoundException(
    //          String.format("Caseworker not found for application id: %s", applicationId)
    //      );
    //    }
    return caseworker != null ? caseworker.getId() : null;
  }
}

package uk.gov.justice.laa.dstew.access.service.applications;

import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    Map<UUID, ProceedingEntity> proceedingEntityMap =
        validateAndMapProceedingsFromAggregate(application, request);

    DecisionEntity decision = buildOrUpdateDecision(application, request);
    request.getProceedings().forEach(req -> updateMeritsDecision(proceedingEntityMap, req));

    application.setDecision(decision);
    application.setModifiedAt(Instant.now());
    application.setIsAutoGranted(request.getAutoGranted());
    applicationRepository.save(application);

    switch (decision.getOverallDecision()) {
      case GRANTED -> handleGrantedDecision(applicationId, caseworkerId, request);
      case REFUSED -> handleRefusedDecision(applicationId, caseworkerId, request);
      default ->
          throw new IllegalStateException("Unexpected value: " + decision.getOverallDecision());
    }
  }

  private void handleGrantedDecision(
      UUID applicationId, UUID caseworkerId, MakeDecisionRequest request) {
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
    saveDomainEventService.saveMakeDecisionDomainEvent(
        applicationId, request, caseworkerId, DomainEventType.APPLICATION_MAKE_DECISION_GRANTED);
  }

  private void handleRefusedDecision(
      UUID applicationId, UUID caseworkerId, MakeDecisionRequest request) {
    certificateRepository.deleteByApplicationId(applicationId);
    saveDomainEventService.saveMakeDecisionDomainEvent(
        applicationId, request, caseworkerId, DomainEventType.APPLICATION_MAKE_DECISION_REFUSED);
  }

  private DecisionEntity buildOrUpdateDecision(
      ApplicationEntity application, MakeDecisionRequest request) {
    DecisionEntity decision =
        Optional.ofNullable(application.getDecision())
            .orElseGet(() -> DecisionEntity.builder().build());
    decision.setOverallDecision(DecisionStatus.valueOf(request.getOverallDecision().getValue()));
    decision.setModifiedAt(Instant.now());
    return decision;
  }

  private void updateMeritsDecision(
      Map<UUID, ProceedingEntity> proceedingEntityMap,
      MakeDecisionProceedingRequest proceedingReq) {
    ProceedingEntity proceedingEntity = proceedingEntityMap.get(proceedingReq.getProceedingId());
    MeritsDecisionEntity meritsDecision =
        Optional.ofNullable(proceedingEntity.getMeritsDecision())
            .orElseGet(MeritsDecisionEntity::new);
    meritsDecision.setDecision(
        MeritsDecisionStatus.valueOf(proceedingReq.getMeritsDecision().getDecision().toString()));
    meritsDecision.setReason(proceedingReq.getMeritsDecision().getReason());
    meritsDecision.setJustification(proceedingReq.getMeritsDecision().getJustification());
    meritsDecision.setModifiedAt(Instant.now());
    proceedingEntity.setMeritsDecision(meritsDecision);
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

    Map<UUID, ProceedingEntity> linkedMap =
        Optional.ofNullable(application.getProceedings()).orElse(Set.of()).stream()
            .collect(Collectors.toMap(ProceedingEntity::getId, p -> p));

    List<UUID> notLinkedIds =
        request.getProceedings().stream()
            .map(MakeDecisionProceedingRequest::getProceedingId)
            .distinct()
            .filter(id -> !linkedMap.containsKey(id))
            .toList();

    if (notLinkedIds.isEmpty()) {
      return linkedMap;
    }

    Set<UUID> foundInDbIds =
        proceedingRepository.findAllById(notLinkedIds).stream()
            .map(ProceedingEntity::getId)
            .collect(Collectors.toSet());

    List<String> errors = new ArrayList<>();

    String notFoundIds =
        notLinkedIds.stream()
            .filter(id -> !foundInDbIds.contains(id))
            .map(UUID::toString)
            .collect(Collectors.joining(","));
    if (!notFoundIds.isEmpty()) {
      errors.add("No proceeding found with id: " + notFoundIds);
    }

    String notLinkedInDbIds =
        foundInDbIds.stream().map(UUID::toString).collect(Collectors.joining(","));
    if (!notLinkedInDbIds.isEmpty()) {
      errors.add("Not linked to application: " + notLinkedInDbIds);
    }

    if (!errors.isEmpty()) {
      throw new ResourceNotFoundException(String.join("; ", errors));
    }

    return linkedMap;
  }

  private static UUID getCaseworkerId(ApplicationEntity application) {
    final CaseworkerEntity caseworker = application.getCaseworker();
    return caseworker != null ? caseworker.getId() : null;
  }
}

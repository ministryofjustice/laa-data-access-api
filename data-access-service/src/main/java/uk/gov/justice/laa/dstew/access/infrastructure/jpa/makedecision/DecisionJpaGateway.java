package uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.domain.DecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.DecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.MeritsDecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.infrastructure.DecisionGateway;

/** JPA implementation of DecisionGateway. No @Component — wired via MakeDecisionConfig. */
public class DecisionJpaGateway implements DecisionGateway {

  private final ApplicationRepository applicationRepository;
  private final DecisionRepository decisionRepository;
  private final MeritsDecisionRepository meritsDecisionRepository;
  private final ProceedingRepository proceedingRepository;
  private final DecisionGatewayMapper mapper;

  /**
   * Constructs DecisionJpaGateway with required repositories and mapper.
   *
   * @param applicationRepository application JPA repository
   * @param decisionRepository decision JPA repository
   * @param meritsDecisionRepository merits decision JPA repository
   * @param proceedingRepository proceeding JPA repository
   * @param mapper decision gateway mapper
   */
  public DecisionJpaGateway(
      ApplicationRepository applicationRepository,
      DecisionRepository decisionRepository,
      MeritsDecisionRepository meritsDecisionRepository,
      ProceedingRepository proceedingRepository,
      DecisionGatewayMapper mapper) {
    this.applicationRepository = applicationRepository;
    this.decisionRepository = decisionRepository;
    this.meritsDecisionRepository = meritsDecisionRepository;
    this.proceedingRepository = proceedingRepository;
    this.mapper = mapper;
  }

  @Override
  public DecisionDomain findByApplicationId(UUID applicationId) {
    ApplicationEntity app =
        applicationRepository
            .findById(applicationId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No application found with id: " + applicationId));
    return app.getDecision() != null ? mapper.toDomain(app.getDecision()) : null;
  }

  @Override
  public DecisionDomain saveAndLink(UUID applicationId, DecisionDomain domain) {
    DecisionEntity entity;
    boolean isNew = domain.id() == null;
    if (isNew) {
      entity = mapper.toNewEntity(domain);
    } else {
      entity =
          decisionRepository
              .findById(domain.id())
              .orElseThrow(
                  () -> new ResourceNotFoundException("No decision found with id: " + domain.id()));
      entity.setOverallDecision(DecisionStatus.valueOf(domain.overallDecision().name()));
      entity.setModifiedAt(Instant.now());
    }

    Set<MeritsDecisionEntity> merits = upsertMeritsDecisions(entity, domain.meritsDecisions());
    entity.setMeritsDecisions(merits);
    DecisionEntity saved = decisionRepository.save(entity);

    if (isNew) {
      ApplicationEntity app =
          applicationRepository
              .findById(applicationId)
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "No application found with id: " + applicationId));
      if (app.getDecision() == null) {
        app.setDecision(saved);
        applicationRepository.save(app);
      }
    }
    return mapper.toDomain(saved);
  }

  private Set<MeritsDecisionEntity> upsertMeritsDecisions(
      DecisionEntity decision, Set<MeritsDecisionDomain> meritsDecisions) {
    Set<MeritsDecisionEntity> existing =
        decision.getMeritsDecisions() != null
            ? new LinkedHashSet<>(decision.getMeritsDecisions())
            : new LinkedHashSet<>();

    for (MeritsDecisionDomain md : meritsDecisions) {
      UUID proceedingId = md.proceedingId();
      MeritsDecisionEntity meritsEntity =
          existing.stream()
              .filter(
                  m -> m.getProceeding() != null && m.getProceeding().getId().equals(proceedingId))
              .findFirst()
              .orElseGet(
                  () -> {
                    MeritsDecisionEntity newEntity = new MeritsDecisionEntity();
                    ProceedingEntity proceeding =
                        proceedingRepository
                            .findById(proceedingId)
                            .orElseThrow(
                                () ->
                                    new ResourceNotFoundException(
                                        "No proceeding found with id: " + proceedingId));
                    newEntity.setProceeding(proceeding);
                    return newEntity;
                  });
      meritsEntity.setDecision(mapper.toEntityDecisionStatus(md.decision()));
      meritsEntity.setReason(md.reason());
      meritsEntity.setJustification(md.justification());
      meritsEntity.setModifiedAt(Instant.now());
      meritsDecisionRepository.save(meritsEntity);
      existing.add(meritsEntity);
    }
    return existing;
  }
}

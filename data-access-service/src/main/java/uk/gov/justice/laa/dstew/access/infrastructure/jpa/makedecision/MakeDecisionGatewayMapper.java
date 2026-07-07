package uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;

/** Converts domain records ↔ JPA entities for operations in the makeDecision use case. */
public class MakeDecisionGatewayMapper {

  /**
   * Mutates the managed {@link ApplicationEntity} in-place from the supplied domain. Does NOT
   * create a new entity — this preserves the JPA-managed {@literal @}Version and all audit fields
   * on the application.
   *
   * <p>Updates: {@code decision} (build-or-update), {@code isAutoGranted}, {@code modifiedAt}, and
   * each proceeding's {@code meritsDecision}. The domain is expected to contain the complete merged
   * set of proceedings (with merits decisions already applied by the use case).
   *
   * @param entity the managed JPA entity to mutate
   * @param domain the updated domain holding the new decision and fully merged proceedings
   */
  public void applyDecisionToEntity(ApplicationEntity entity, ApplicationDomain domain) {
    DecisionEntity decisionEntity =
        entity.getDecision() != null ? entity.getDecision() : DecisionEntity.builder().build();

    decisionEntity.setOverallDecision(
        domain.decision().overallDecision() != null
            ? DecisionStatus.valueOf(domain.decision().overallDecision().name())
            : null);

    entity.setDecision(decisionEntity);

    entity.setIsAutoGranted(domain.isAutoGranted());

    if (entity.getProceedings() != null && domain.proceedings() != null) {

      Map<UUID, ProceedingEntity> proceedingEntityMap =
          entity.getProceedings().stream()
              .collect(Collectors.toMap(ProceedingEntity::getId, p -> p));

      domain
          .proceedings()
          .forEach(
              proceedingDomain -> applyChangesToProceeding(proceedingEntityMap, proceedingDomain));
    }
  }

  private void applyChangesToProceeding(
      Map<UUID, ProceedingEntity> proceedingEntityMap, ProceedingDomain proceedingDomain) {
    ProceedingEntity proceedingEntity = proceedingEntityMap.get(proceedingDomain.id());
    if (proceedingEntity != null && proceedingDomain.meritsDecision() != null) {
      MeritsDecisionEntity meritsDecision =
          proceedingEntity.getMeritsDecision() != null
              ? proceedingEntity.getMeritsDecision()
              : new MeritsDecisionEntity();
      meritsDecision.setDecision(
          proceedingDomain.meritsDecision().decision() != null
              ? MeritsDecisionStatus.valueOf(proceedingDomain.meritsDecision().decision().name())
              : null);
      meritsDecision.setReason(proceedingDomain.meritsDecision().reason());
      meritsDecision.setJustification(proceedingDomain.meritsDecision().justification());
      proceedingEntity.setMeritsDecision(meritsDecision);
    }
  }
}

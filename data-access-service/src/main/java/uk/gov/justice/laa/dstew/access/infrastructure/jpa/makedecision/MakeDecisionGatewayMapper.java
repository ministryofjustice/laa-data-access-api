package uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;

/** Converts domain records ↔ JPA entities for operations in the makeDecision use case. */
public class MakeDecisionGatewayMapper {

  /**
   * Maps a {@link CertificateEntity} to a {@link CertificateDomain}.
   *
   * @param certificateEntity the JPA certificate entity
   * @return the domain
   */
  public CertificateDomain toCertificateDomain(CertificateEntity certificateEntity) {
    return CertificateDomain.builder()
        .id(certificateEntity.getId())
        .applicationId(certificateEntity.getApplicationId())
        .certificateContent(certificateEntity.getCertificateContent())
        .createdBy(certificateEntity.getCreatedBy())
        .updatedBy(certificateEntity.getUpdatedBy())
        .build();
  }

  /**
   * Maps a {@link CertificateDomain} to a new {@link CertificateEntity}. Use only for INSERT (id is
   * null). For UPDATE, use the load-and-apply pattern.
   *
   * @param certificateDomain the domain
   * @return a new entity for persistence
   */
  public CertificateEntity toCertificateEntity(CertificateDomain certificateDomain) {
    CertificateEntity entity = new CertificateEntity();
    entity.setApplicationId(certificateDomain.applicationId());
    entity.setCertificateContent(certificateDomain.certificateContent());
    entity.setCreatedBy(certificateDomain.createdBy());
    entity.setUpdatedBy(certificateDomain.updatedBy());
    return entity;
  }

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
        Optional.ofNullable(entity.getDecision()).orElseGet(() -> DecisionEntity.builder().build());
    decisionEntity.setOverallDecision(DecisionStatus.valueOf(domain.decision().overallDecision()));
    decisionEntity.setModifiedAt(domain.decision().modifiedAt());
    entity.setDecision(decisionEntity);

    entity.setIsAutoGranted(domain.isAutoGranted());
    entity.setModifiedAt(Instant.now());

    if (entity.getProceedings() != null && domain.proceedings() != null) {
      Map<UUID, ProceedingEntity> proceedingEntityMap =
          entity.getProceedings().stream()
              .collect(Collectors.toMap(ProceedingEntity::getId, p -> p));
      domain
          .proceedings()
          .forEach(
              proceedingDomain -> {
                ProceedingEntity proceedingEntity = proceedingEntityMap.get(proceedingDomain.id());
                if (proceedingEntity != null && proceedingDomain.meritsDecision() != null) {
                  MeritsDecisionEntity meritsDecision =
                      Optional.ofNullable(proceedingEntity.getMeritsDecision())
                          .orElseGet(MeritsDecisionEntity::new);
                  meritsDecision.setDecision(
                      MeritsDecisionStatus.valueOf(proceedingDomain.meritsDecision().decision()));
                  meritsDecision.setReason(proceedingDomain.meritsDecision().reason());
                  meritsDecision.setJustification(
                      proceedingDomain.meritsDecision().justification());
                  meritsDecision.setModifiedAt(proceedingDomain.meritsDecision().modifiedAt());
                  proceedingEntity.setMeritsDecision(meritsDecision);
                }
              });
    }
  }
}

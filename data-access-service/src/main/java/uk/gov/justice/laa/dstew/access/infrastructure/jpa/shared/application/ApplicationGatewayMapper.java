package uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.application;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.DecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.IndividualDomain;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.MatterType;

/** Converts between domain records and JPA entities for the createApplication use case. */
public class ApplicationGatewayMapper {

  // ── INSERT path ─────────────────────────────────────────────────────────

  /**
   * Converts an {@link ApplicationDomain} to a new {@link ApplicationEntity} for INSERT.
   *
   * @param application the application record (id and createdAt are null for INSERT)
   * @return the entity ready for persistence
   */
  public ApplicationEntity toApplicationEntity(ApplicationDomain application) {
    ApplicationEntity entity = new ApplicationEntity();
    entity.setStatus(ApplicationStatus.valueOf(application.status()));
    entity.setLaaReference(application.laaReference());
    entity.setOfficeCode(application.officeCode());
    entity.setApplicationContent(application.applicationContent());
    entity.setSchemaVersion(application.schemaVersion());
    entity.setApplyApplicationId(application.applyApplicationId());
    entity.setSubmittedAt(application.submittedAt());
    entity.setUsedDelegatedFunctions(application.usedDelegatedFunctions());
    entity.setCategoryOfLaw(
        application.categoryOfLaw() != null
            ? CategoryOfLaw.valueOf(application.categoryOfLaw())
            : null);
    entity.setMatterType(
        application.matterType() != null ? MatterType.valueOf(application.matterType()) : null);
    entity.setIsAutoGranted(application.isAutoGranted());

    if (application.individuals() != null) {
      entity.setIndividuals(
          application.individuals().stream()
              .map(this::toIndividualEntity)
              .collect(Collectors.toSet()));
    }
    if (application.proceedings() != null && !application.proceedings().isEmpty()) {
      entity.setProceedings(
          application.proceedings().stream()
              .filter(Objects::nonNull)
              .map(this::toProceedingEntity)
              .collect(Collectors.toCollection(LinkedHashSet::new)));
    }
    return entity;
  }

  // ── Entity → Domain (read path) ─────────────────────────────────────────

  /**
   * Converts an {@link ApplicationEntity} to an {@link ApplicationDomain}.
   *
   * @param application the JPA application
   * @return the domain record
   */
  public ApplicationDomain toApplicationDomain(ApplicationEntity application) {
    return ApplicationDomain.builder()
        .id(application.getId())
        .version(application.getVersion())
        .status(application.getStatus() != null ? application.getStatus().name() : null)
        .laaReference(application.getLaaReference())
        .officeCode(application.getOfficeCode())
        .applicationContent(application.getApplicationContent())
        .schemaVersion(application.getSchemaVersion())
        .createdAt(application.getCreatedAt())
        .modifiedAt(application.getModifiedAt())
        .applyApplicationId(application.getApplyApplicationId())
        .submittedAt(application.getSubmittedAt())
        .usedDelegatedFunctions(application.getUsedDelegatedFunctions())
        .categoryOfLaw(
            application.getCategoryOfLaw() != null ? application.getCategoryOfLaw().name() : null)
        .matterType(application.getMatterType() != null ? application.getMatterType().name() : null)
        .isAutoGranted(application.getIsAutoGranted())
        .individuals(
            application.getIndividuals() == null
                ? Set.of()
                : application.getIndividuals().stream()
                    .map(this::toIndividualDomain)
                    .collect(Collectors.toSet()))
        .proceedings(
            application.getProceedings() == null
                ? Set.of()
                : application.getProceedings().stream()
                    .map(this::toProceedingDomain)
                    .collect(Collectors.toCollection(LinkedHashSet::new)))
        .caseworkerId(
            application.getCaseworker() != null ? application.getCaseworker().getId() : null)
        .decision(toDecisionDomain(application.getDecision()))
        .build();
  }

  /**
   * Maps a {@link DecisionEntity} to a {@link DecisionDomain}, nullable-safe.
   *
   * @param decision the JPA decision entity; may be {@code null}
   * @return the domain, or {@code null} if {@code decision} is {@code null}
   */
  public DecisionDomain toDecisionDomain(DecisionEntity decision) {
    if (decision == null) {
      return null;
    }
    return DecisionDomain.builder()
        .overallDecision(
            decision.getOverallDecision() != null ? decision.getOverallDecision().name() : null)
        .modifiedAt(decision.getModifiedAt())
        .build();
  }

  /**
   * Maps a {@link MeritsDecisionEntity} to a {@link MeritsDecisionDomain}, nullable-safe.
   *
   * @param meritsDecision the JPA merits decision entity; may be {@code null}
   * @return the domain, or {@code null} if {@code meritsDecision} is {@code null}
   */
  public MeritsDecisionDomain toMeritsDecisionDomain(MeritsDecisionEntity meritsDecision) {
    if (meritsDecision == null) {
      return null;
    }
    return MeritsDecisionDomain.builder()
        .decision(meritsDecision.getDecision() != null ? meritsDecision.getDecision().name() : null)
        .reason(meritsDecision.getReason())
        .justification(meritsDecision.getJustification())
        .modifiedAt(meritsDecision.getModifiedAt())
        .build();
  }

  // ── Private helpers ──────────────────────────────────────────────────────

  private IndividualEntity toIndividualEntity(IndividualDomain individual) {
    IndividualEntity entity = new IndividualEntity();
    entity.setFirstName(individual.firstName());
    entity.setLastName(individual.lastName());
    entity.setDateOfBirth(individual.dateOfBirth());
    entity.setIndividualContent(individual.individualContent());
    entity.setType(individual.type() != null ? IndividualType.valueOf(individual.type()) : null);
    return entity;
  }

  private IndividualDomain toIndividualDomain(IndividualEntity individual) {
    return IndividualDomain.builder()
        .id(individual.getId())
        .firstName(individual.getFirstName())
        .lastName(individual.getLastName())
        .dateOfBirth(individual.getDateOfBirth())
        .individualContent(individual.getIndividualContent())
        .type(individual.getType() != null ? individual.getType().name() : null)
        .build();
  }

  private ProceedingEntity toProceedingEntity(ProceedingDomain proceeding) {
    ProceedingEntity entity = new ProceedingEntity();
    entity.setApplyProceedingId(proceeding.applyProceedingId());
    entity.setLead(proceeding.isLead());
    entity.setDescription(proceeding.description());
    entity.setProceedingContent(proceeding.proceedingContent());
    entity.setCreatedBy(proceeding.createdBy() != null ? proceeding.createdBy() : "");
    entity.setUpdatedBy(proceeding.updatedBy() != null ? proceeding.updatedBy() : "");
    return entity;
  }

  private ProceedingDomain toProceedingDomain(ProceedingEntity proceeding) {
    return ProceedingDomain.builder()
        .id(proceeding.getId())
        .applyProceedingId(proceeding.getApplyProceedingId())
        .description(proceeding.getDescription())
        .isLead(proceeding.isLead())
        .proceedingContent(proceeding.getProceedingContent())
        .createdBy(proceeding.getCreatedBy())
        .updatedBy(proceeding.getUpdatedBy())
        .meritsDecision(toMeritsDecisionDomain(proceeding.getMeritsDecision()))
        .build();
  }
}

package uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.application;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.IndividualDomain;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.MatterType;

/** Converts between domain records and JPA entities for the createApplication use case. */
public class ApplicationGatewayMapper {

  private final ObjectMapper objectMapper;

  /**
   * Constructs the mapper with the given Jackson ObjectMapper.
   *
   * @param objectMapper the Jackson ObjectMapper
   */
  public ApplicationGatewayMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  // ── INSERT path ─────────────────────────────────────────────────────────

  /**
   * Converts an {@link ApplicationDomain} to a new {@link ApplicationEntity} for INSERT.
   *
   * @param domain the domain record (id and createdAt are null for INSERT)
   * @return the entity ready for persistence
   */
  public ApplicationEntity toEntity(ApplicationDomain domain) {
    ApplicationEntity entity = new ApplicationEntity();
    entity.setStatus(ApplicationStatus.valueOf(domain.status()));
    entity.setLaaReference(domain.laaReference());
    entity.setOfficeCode(domain.officeCode());
    entity.setApplicationContent(domain.applicationContent());
    entity.setSchemaVersion(domain.schemaVersion());
    entity.setApplyApplicationId(domain.applyApplicationId());
    entity.setSubmittedAt(domain.submittedAt());
    entity.setUsedDelegatedFunctions(domain.usedDelegatedFunctions());
    entity.setCategoryOfLaw(
        domain.categoryOfLaw() != null ? CategoryOfLaw.valueOf(domain.categoryOfLaw()) : null);
    entity.setMatterType(
        domain.matterType() != null ? MatterType.valueOf(domain.matterType()) : null);
    entity.setIsAutoGranted(domain.isAutoGranted());

    if (domain.individuals() != null) {
      entity.setIndividuals(
          domain.individuals().stream().map(this::toIndividualEntity).collect(Collectors.toSet()));
    }
    if (domain.proceedings() != null && !domain.proceedings().isEmpty()) {
      entity.setProceedings(
          domain.proceedings().stream()
              .filter(Objects::nonNull)
              .map(this::toProceedingEntity)
              .collect(Collectors.toCollection(LinkedHashSet::new)));
    }
    return entity;
  }

  /** Enriches a pre-save domain record with the id and createdAt returned by the database. */
  public ApplicationDomain enrichWithSavedFields(
      ApplicationDomain domain, ApplicationEntity saved) {
    return domain.toBuilder().id(saved.getId()).createdAt(saved.getCreatedAt()).build();
  }

  // ── Entity → Domain (read path) ─────────────────────────────────────────

  /**
   * Converts an {@link ApplicationEntity} to an {@link ApplicationDomain}.
   *
   * @param entity the JPA entity
   * @return the domain record
   */
  public ApplicationDomain toDomain(ApplicationEntity entity) {
    return ApplicationDomain.builder()
        .id(entity.getId())
        .status(entity.getStatus() != null ? entity.getStatus().name() : null)
        .laaReference(entity.getLaaReference())
        .officeCode(entity.getOfficeCode())
        .applicationContent(entity.getApplicationContent())
        .schemaVersion(entity.getSchemaVersion())
        .createdAt(entity.getCreatedAt())
        .modifiedAt(entity.getModifiedAt())
        .applyApplicationId(entity.getApplyApplicationId())
        .submittedAt(entity.getSubmittedAt())
        .usedDelegatedFunctions(entity.getUsedDelegatedFunctions())
        .categoryOfLaw(entity.getCategoryOfLaw() != null ? entity.getCategoryOfLaw().name() : null)
        .matterType(entity.getMatterType() != null ? entity.getMatterType().name() : null)
        .isAutoGranted(entity.getIsAutoGranted())
        .individuals(
            entity.getIndividuals() == null
                ? Set.of()
                : entity.getIndividuals().stream()
                    .map(this::toIndividualDomain)
                    .collect(Collectors.toSet()))
        .proceedings(
            entity.getProceedings() == null
                ? Set.of()
                : entity.getProceedings().stream()
                    .map(this::toProceedingDomain)
                    .collect(Collectors.toCollection(LinkedHashSet::new)))
        .build();
  }

  // ── Private helpers ──────────────────────────────────────────────────────

  private IndividualEntity toIndividualEntity(IndividualDomain domain) {
    IndividualEntity entity = new IndividualEntity();
    entity.setFirstName(domain.firstName());
    entity.setLastName(domain.lastName());
    entity.setDateOfBirth(domain.dateOfBirth());
    entity.setIndividualContent(domain.individualContent());
    entity.setType(domain.type() != null ? IndividualType.valueOf(domain.type()) : null);
    return entity;
  }

  private IndividualDomain toIndividualDomain(IndividualEntity entity) {
    return IndividualDomain.builder()
        .id(entity.getId())
        .firstName(entity.getFirstName())
        .lastName(entity.getLastName())
        .dateOfBirth(entity.getDateOfBirth())
        .individualContent(entity.getIndividualContent())
        .type(entity.getType() != null ? entity.getType().name() : null)
        .build();
  }

  private ProceedingEntity toProceedingEntity(ProceedingDomain domain) {
    ProceedingEntity entity = new ProceedingEntity();
    entity.setApplyProceedingId(domain.applyProceedingId());
    entity.setLead(domain.isLead());
    entity.setDescription(domain.description());
    entity.setProceedingContent(domain.proceedingContent());
    entity.setCreatedBy(domain.createdBy() != null ? domain.createdBy() : "");
    entity.setUpdatedBy(domain.updatedBy() != null ? domain.updatedBy() : "");
    return entity;
  }

  private ProceedingDomain toProceedingDomain(ProceedingEntity entity) {
    return ProceedingDomain.builder()
        .id(entity.getId())
        .applyProceedingId(entity.getApplyProceedingId())
        .description(entity.getDescription())
        .isLead(entity.isLead())
        .proceedingContent(entity.getProceedingContent())
        .createdBy(entity.getCreatedBy())
        .updatedBy(entity.getUpdatedBy())
        .build();
  }
}

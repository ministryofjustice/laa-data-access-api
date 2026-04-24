package uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.Individual;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntityV2;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntityV2;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

/** Maps between ApplicationDomain and ApplicationEntityV2. */
public class ApplicationGatewayMapper {

  private final ProceedingGatewayMapper proceedingMapper = new ProceedingGatewayMapper();
  private final DecisionGatewayMapper decisionMapper = new DecisionGatewayMapper();

  /** Converts an ApplicationEntityV2 to a domain record (full sub-graph). */
  public ApplicationDomain toDomain(ApplicationEntityV2 entity) {
    if (entity == null) {
      return null;
    }
    // Only access individuals if the collection is already initialised (i.e. eagerly joined by
    // the caller). Triggering a lazy-load here causes a redundant SELECT on every aggregate load
    // even when individuals are not needed (e.g. makeDecision).
    List<Individual> individuals =
        org.hibernate.Hibernate.isInitialized(entity.getIndividuals())
            ? entity.getIndividuals().stream().map(this::toIndividual).toList()
            : List.of();
    List<ProceedingDomain> proceedings =
        entity.getProceedings() == null
            ? List.of()
            : entity.getProceedings().stream().map(proceedingMapper::toDomain).toList();
    return ApplicationDomain.builder()
        .id(entity.getId())
        .status(toDomainStatus(entity.getStatus()))
        .laaReference(entity.getLaaReference())
        .officeCode(entity.getOfficeCode())
        .applyApplicationId(entity.getApplyApplicationId())
        .usedDelegatedFunctions(entity.getUsedDelegatedFunctions())
        .categoryOfLaw(toDomainCategoryOfLaw(entity.getCategoryOfLaw()))
        .matterType(toDomainMatterType(entity.getMatterType()))
        .submittedAt(entity.getSubmittedAt())
        .createdAt(entity.getCreatedAt())
        .applicationContent(entity.getApplicationContent())
        .individuals(individuals)
        .schemaVersion(entity.getSchemaVersion() != null ? entity.getSchemaVersion() : 0)
        .version(entity.getVersion())
        .caseworkerId(entity.getCaseworker() != null ? entity.getCaseworker().getId() : null)
        .isAutoGranted(entity.getIsAutoGranted())
        .proceedings(proceedings)
        .decision(decisionMapper.toDomain(entity.getDecision()))
        .build();
  }

  /** Builds a fresh ApplicationEntityV2 from domain (INSERT path — id is null). */
  public ApplicationEntityV2 toNewEntity(ApplicationDomain domain) {
    ApplicationEntityV2 entity = new ApplicationEntityV2();
    entity.setStatus(toModelStatus(domain.status()));
    entity.setLaaReference(domain.laaReference());
    entity.setOfficeCode(domain.officeCode());
    entity.setApplyApplicationId(domain.applyApplicationId());
    entity.setUsedDelegatedFunctions(domain.usedDelegatedFunctions());
    entity.setCategoryOfLaw(toModelCategoryOfLaw(domain.categoryOfLaw()));
    entity.setMatterType(toModelMatterType(domain.matterType()));
    entity.setSubmittedAt(domain.submittedAt());
    entity.setApplicationContent(domain.applicationContent());
    entity.setSchemaVersion(domain.schemaVersion());
    if (domain.individuals() != null) {
      Set<IndividualEntity> individualEntities =
          domain.individuals().stream().map(this::toIndividualEntity).collect(Collectors.toSet());
      entity.setIndividuals(individualEntities);
    }
    List<ProceedingEntityV2> proceedingEntities =
        domain.proceedings() != null
            ? domain.proceedings().stream().map(proceedingMapper::toNewEntity).toList()
            : new ArrayList<>();
    entity.setProceedings(new ArrayList<>(proceedingEntities));
    if (domain.decision() != null) {
      entity.setDecision(decisionMapper.toNewEntity(domain.decision()));
    }
    return entity;
  }

  /** Mutates a managed ApplicationEntityV2 in-place (UPDATE path). */
  public void applyToEntity(ApplicationDomain domain, ApplicationEntityV2 entity) {
    entity.setIsAutoGranted(domain.isAutoGranted());
    // Reconcile proceedings — match by id, update existing or add new
    if (domain.proceedings() != null) {
      List<ProceedingEntityV2> existing = entity.getProceedings();
      for (ProceedingDomain pd : domain.proceedings()) {
        if (pd.id() != null) {
          existing.stream()
              .filter(e -> e.getId().equals(pd.id()))
              .findFirst()
              .ifPresent(e -> proceedingMapper.applyToEntity(pd, e));
        } else {
          existing.add(proceedingMapper.toNewEntity(pd));
        }
      }
    }
    // Reconcile decision
    if (domain.decision() != null) {
      if (entity.getDecision() != null) {
        decisionMapper.applyToEntity(domain.decision(), entity.getDecision());
      } else {
        entity.setDecision(decisionMapper.toNewEntity(domain.decision()));
      }
    }
  }

  private IndividualEntity toIndividualEntity(Individual individual) {
    IndividualEntity entity = new IndividualEntity();
    entity.setFirstName(individual.firstName());
    entity.setLastName(individual.lastName());
    entity.setDateOfBirth(individual.dateOfBirth());
    entity.setIndividualContent(individual.details());
    if (individual.type() != null) {
      entity.setType(IndividualType.valueOf(individual.type()));
    }
    return entity;
  }

  private Individual toIndividual(IndividualEntity entity) {
    return new Individual(
        entity.getFirstName(),
        entity.getLastName(),
        entity.getDateOfBirth(),
        entity.getIndividualContent(),
        entity.getType() != null ? entity.getType().name() : null);
  }

  private uk.gov.justice.laa.dstew.access.model.ApplicationStatus toModelStatus(
      uk.gov.justice.laa.dstew.access.domain.ApplicationStatus status) {
    if (status == null) {
      return null;
    }
    return uk.gov.justice.laa.dstew.access.model.ApplicationStatus.valueOf(status.name());
  }

  private uk.gov.justice.laa.dstew.access.domain.ApplicationStatus toDomainStatus(
      uk.gov.justice.laa.dstew.access.model.ApplicationStatus status) {
    if (status == null) {
      return null;
    }
    return uk.gov.justice.laa.dstew.access.domain.ApplicationStatus.valueOf(status.name());
  }

  private uk.gov.justice.laa.dstew.access.model.CategoryOfLaw toModelCategoryOfLaw(
      uk.gov.justice.laa.dstew.access.domain.CategoryOfLaw col) {
    if (col == null) {
      return null;
    }
    return uk.gov.justice.laa.dstew.access.model.CategoryOfLaw.valueOf(col.name());
  }

  private uk.gov.justice.laa.dstew.access.domain.CategoryOfLaw toDomainCategoryOfLaw(
      uk.gov.justice.laa.dstew.access.model.CategoryOfLaw col) {
    if (col == null) {
      return null;
    }
    return uk.gov.justice.laa.dstew.access.domain.CategoryOfLaw.valueOf(col.name());
  }

  private uk.gov.justice.laa.dstew.access.model.MatterType toModelMatterType(
      uk.gov.justice.laa.dstew.access.domain.MatterType mt) {
    if (mt == null) {
      return null;
    }
    return uk.gov.justice.laa.dstew.access.model.MatterType.valueOf(mt.name());
  }

  private uk.gov.justice.laa.dstew.access.domain.MatterType toDomainMatterType(
      uk.gov.justice.laa.dstew.access.model.MatterType mt) {
    if (mt == null) {
      return null;
    }
    return uk.gov.justice.laa.dstew.access.domain.MatterType.valueOf(mt.name());
  }
}

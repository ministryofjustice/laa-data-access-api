package uk.gov.justice.laa.dstew.access.infrastructure.jpa.createapplication;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.Individual;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

/** Maps between ApplicationDomain and ApplicationEntity. */
public class ApplicationGatewayMapper {

  /** Converts a domain record to a JPA entity. */
  public ApplicationEntity toEntity(ApplicationDomain domain) {
    if (domain == null) {
      return null;
    }
    ApplicationEntity entity = new ApplicationEntity();
    entity.setId(domain.id());
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
    return entity;
  }

  /** Converts a JPA entity to a domain record. */
  public ApplicationDomain toDomain(ApplicationEntity entity) {
    if (entity == null) {
      return null;
    }
    List<Individual> individuals =
        entity.getIndividuals() == null
            ? List.of()
            : entity.getIndividuals().stream().map(this::toIndividual).toList();
    return new ApplicationDomain(
        entity.getId(),
        toDomainStatus(entity.getStatus()),
        entity.getLaaReference(),
        entity.getOfficeCode(),
        entity.getApplyApplicationId(),
        entity.getUsedDelegatedFunctions(),
        toDomainCategoryOfLaw(entity.getCategoryOfLaw()),
        toDomainMatterType(entity.getMatterType()),
        entity.getSubmittedAt(),
        entity.getCreatedAt(),
        entity.getApplicationContent(),
        individuals,
        entity.getSchemaVersion() != null ? entity.getSchemaVersion() : 0);
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

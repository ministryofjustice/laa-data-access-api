package uk.gov.justice.laa.dstew.access.adapter.outbound.persistence;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.domain.model.Application;
import uk.gov.justice.laa.dstew.access.domain.model.Individual;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;

/**
 * Maps between domain model objects and JPA entity objects. This mapper lives in the persistence
 * adapter layer and is the only place where domain ↔ entity translation occurs.
 */
@Component
public class DomainEntityMapper {

  /**
   * Maps an {@link ApplicationEntity} to a domain {@link Application}.
   *
   * @param entity the JPA entity
   * @return the domain model, or {@code null} if the entity is null
   */
  public Application toDomain(ApplicationEntity entity) {
    if (entity == null) {
      return null;
    }
    return Application.builder()
        .id(entity.getId())
        .version(entity.getVersion())
        .status(entity.getStatus())
        .laaReference(entity.getLaaReference())
        .officeCode(entity.getOfficeCode())
        .applicationContent(entity.getApplicationContent())
        .individuals(mapIndividualsToDomain(entity.getIndividuals()))
        .schemaVersion(entity.getSchemaVersion())
        .createdAt(entity.getCreatedAt())
        .modifiedAt(entity.getModifiedAt())
        .applyApplicationId(entity.getApplyApplicationId())
        .submittedAt(entity.getSubmittedAt())
        .usedDelegatedFunctions(entity.getUsedDelegatedFunctions())
        .categoryOfLaw(entity.getCategoryOfLaw())
        .matterType(entity.getMatterType())
        .linkedApplications(mapLinkedApplicationsToDomain(entity.getLinkedApplications()))
        .build();
  }

  /**
   * Maps an {@link IndividualEntity} to a domain {@link Individual}.
   *
   * @param entity the JPA entity
   * @return the domain individual
   */
  public Individual toDomain(IndividualEntity entity) {
    if (entity == null) {
      return null;
    }
    return Individual.builder()
        .id(entity.getId())
        .firstName(entity.getFirstName())
        .lastName(entity.getLastName())
        .dateOfBirth(entity.getDateOfBirth())
        .individualContent(entity.getIndividualContent())
        .type(entity.getType())
        .createdAt(entity.getCreatedAt())
        .modifiedAt(entity.getModifiedAt())
        .build();
  }

  /**
   * Maps a domain {@link Application} to an {@link ApplicationEntity}.
   *
   * @param domain the domain model
   * @return the JPA entity, or {@code null} if the domain object is null
   */
  public ApplicationEntity toEntity(Application domain) {
    if (domain == null) {
      return null;
    }
    return ApplicationEntity.builder()
        .id(domain.getId())
        .version(domain.getVersion())
        .status(domain.getStatus())
        .laaReference(domain.getLaaReference())
        .officeCode(domain.getOfficeCode())
        .applicationContent(domain.getApplicationContent())
        .individuals(mapIndividualsToEntity(domain.getIndividuals()))
        .schemaVersion(domain.getSchemaVersion())
        .createdAt(domain.getCreatedAt())
        .modifiedAt(domain.getModifiedAt())
        .applyApplicationId(domain.getApplyApplicationId())
        .submittedAt(domain.getSubmittedAt())
        .usedDelegatedFunctions(domain.getUsedDelegatedFunctions())
        .categoryOfLaw(domain.getCategoryOfLaw())
        .matterType(domain.getMatterType())
        .linkedApplications(mapLinkedApplicationsToEntity(domain.getLinkedApplications()))
        .build();
  }

  /**
   * Maps a domain {@link Individual} to an {@link IndividualEntity}.
   *
   * @param domain the domain individual
   * @return the JPA entity
   */
  public IndividualEntity toEntity(Individual domain) {
    if (domain == null) {
      return null;
    }
    return IndividualEntity.builder()
        .id(domain.getId())
        .firstName(domain.getFirstName())
        .lastName(domain.getLastName())
        .dateOfBirth(domain.getDateOfBirth())
        .individualContent(domain.getIndividualContent())
        .type(domain.getType())
        .build();
  }

  private Set<Individual> mapIndividualsToDomain(Set<IndividualEntity> entities) {
    if (entities == null) {
      return null;
    }
    return entities.stream().map(this::toDomain).collect(Collectors.toSet());
  }

  private Set<IndividualEntity> mapIndividualsToEntity(Set<Individual> individuals) {
    if (individuals == null) {
      return null;
    }
    return individuals.stream().map(this::toEntity).collect(Collectors.toSet());
  }

  private Set<ApplicationEntity> mapLinkedApplicationsToEntity(Set<Application> applications) {
    if (applications == null) {
      return null;
    }
    Set<ApplicationEntity> result = new HashSet<>();
    for (Application app : applications) {
      // Shallow mapping — only the ID is needed for JPA relationship management
      result.add(
          ApplicationEntity.builder()
              .id(app.getId())
              .version(app.getVersion())
              .status(app.getStatus())
              .laaReference(app.getLaaReference())
              .applyApplicationId(app.getApplyApplicationId())
              .build());
    }
    return result;
  }

  private Set<Application> mapLinkedApplicationsToDomain(Set<ApplicationEntity> entities) {
    if (entities == null) {
      return null;
    }
    Set<Application> result = new HashSet<>();
    for (ApplicationEntity entity : entities) {
      // Shallow mapping for linked applications — do not recurse into their linked applications
      result.add(
          Application.builder()
              .id(entity.getId())
              .version(entity.getVersion())
              .status(entity.getStatus())
              .laaReference(entity.getLaaReference())
              .applyApplicationId(entity.getApplyApplicationId())
              .build());
    }
    return result;
  }
}

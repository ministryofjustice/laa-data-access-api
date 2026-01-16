package uk.gov.justice.laa.dstew.access.mapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.Individual;

/**
 * Mapper interface. All mapping operations are performed safely, throwing an {@link
 * IllegalArgumentException} if JSON conversion fails.
 */
@Mapper(
    componentModel = "spring",
    uses = {IndividualMapper.class})
public interface ApplicationMapper {

  IndividualMapper individualMapper = Mappers.getMapper(IndividualMapper.class);

  /**
   * Converts a {@link ApplicationCreateRequest} model into a new {@link ApplicationEntity}.
   *
   * @param req the CREATE request to map
   * @return a new {@link ApplicationEntity} populated from the request, or {@code null} if the
   *     request is null
   * @throws IllegalArgumentException if the {@code applicationContent} cannot be serialized
   */
  default ApplicationEntity toApplicationEntity(ApplicationCreateRequest req) {
    if (req == null) {
      return null;
    }
    ApplicationEntity entity = new ApplicationEntity();
    entity.setStatus(req.getStatus());
    entity.setLaaReference(req.getLaaReference());
    var individuals =
        req.getIndividuals().stream()
            .map(individualMapper::toIndividualEntity)
            .collect(Collectors.toSet());
    entity.setApplicationContent(req.getApplicationContent());
    entity.setIndividuals(individuals);
    return entity;
  }

  /**
   * Updates an existing {@link ApplicationEntity} using values from an {@link
   * ApplicationUpdateRequest}.
   *
   * @param entity the entity to update
   * @param req the update request containing new values
   * @throws IllegalArgumentException if the {@code applicationContent} cannot be serialized
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  default void updateApplicationEntity(
      @MappingTarget ApplicationEntity entity, ApplicationUpdateRequest req) {
    if (req.getStatus() != null) {
      entity.setStatus(req.getStatus());
    }
    entity.setApplicationContent(req.getApplicationContent());
  }

  /**
   * Maps a {@link ApplicationEntity} to an API-facing {@link Application} model.
   *
   * @param entity the entity to map
   * @return a new {@link Application} sobject, or {@code null} if the entity is null
   * @throws IllegalArgumentException if the {@code applicationContent} cannot be deserialized
   */
  default Application toApplication(ApplicationEntity entity) {
    if (entity == null) {
      return null;
    }

    Application application = new Application();
    application.setId(entity.getId());
    application.setStatus(entity.getStatus());
    application.setSchemaVersion(entity.getSchemaVersion());
    application.setApplicationContent(entity.getApplicationContent());
    application.setLaaReference(entity.getLaaReference());
    application.caseworkerId(
        entity.getCaseworker() != null ? entity.getCaseworker().getId() : null);
    application.setCreatedAt(OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC));
    application.setUpdatedAt(OffsetDateTime.ofInstant(entity.getUpdatedAt(), ZoneOffset.UTC));

    application.setIndividuals(getIndividuals(entity.getIndividuals()));

    return application;
  }

  private static List<Individual> getIndividuals(Set<IndividualEntity> individuals) {
    return individuals.stream()
        .map(individualMapper::toIndividual)
        .filter(Objects::nonNull)
        .toList();
  }
}

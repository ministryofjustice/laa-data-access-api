package uk.gov.justice.laa.dstew.access.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.uuid.Generators;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;


/**
 * Mapper interface.
 * All mapping operations are performed safely, throwing an
 * {@link IllegalArgumentException} if JSON conversion fails.
 */
@Mapper(componentModel = "spring", uses = {IndividualMapper.class})
public interface ApplicationMapper {

  ObjectMapper objectMapper = new ObjectMapper();

  IndividualMapper individualMapper = Mappers.getMapper(IndividualMapper.class);

  /**
   * Converts a {@link ApplicationCreateRequest} model into a new {@link ApplicationEntity}.
   *
   * @param req the CREATE request to map
   * @return a new {@link ApplicationEntity} populated from the request, or {@code null} if the request is null
   * @throws IllegalArgumentException if the {@code applicationContent} cannot be serialized
   */
  default ApplicationEntity toApplicationEntity(ApplicationCreateRequest req) {
    if (req == null) {
      return null;
    }

    ApplicationEntity entity = new ApplicationEntity();
    entity.setId(Generators.timeBasedEpochGenerator().generate());
    entity.setStatus(req.getStatus());
    entity.setApplicationReference(req.getApplicationReference());

    try {
      entity.setApplicationContent(
          objectMapper.convertValue(req.getApplicationContent(), Map.class));
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Failed to serialize ApplicationCreateRequest.applicationContent", e);
    }
    return entity;
  }

  /**
   * Updates an existing {@link ApplicationEntity} using values from an {@link ApplicationUpdateRequest}.
   *
   * @param entity the entity to update
   * @param req the update request containing new values
   * @throws IllegalArgumentException if the {@code applicationContent} cannot be serialized
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  default void updateApplicationEntity(@MappingTarget ApplicationEntity entity, ApplicationUpdateRequest req) {
    if (req.getStatus() != null) {
      entity.setStatus(req.getStatus());
    }
    if (req.getApplicationContent() != null) {
      try {
        entity.setApplicationContent(
            objectMapper.convertValue(req.getApplicationContent(), Map.class));
      } catch (Exception e) {
        throw new IllegalArgumentException(
            "Failed to serialize ApplicationUpdateRequest.applicationContent", e);
      }
    }
  }

  /**
   * Maps a {@link ApplicationEntity} to an API-facing {@link Application} model.
   *
   * @param entity the entity to map
   * @return a new {@link Application} object, or {@code null} if the entity is null
   * @throws IllegalArgumentException if the {@code applicationContent} cannot be deserialized
   */
  default Application toApplication(ApplicationEntity entity) {
    if (entity == null) {
      return null;
    }

    try {
      Application application = new Application();
      application.setId(entity.getId());
      application.setApplicationStatus(entity.getStatus());
      application.setSchemaVersion(entity.getSchemaVersion());
      application.setApplicationContent(
          OBJECT_MAPPER.convertValue(entity.getApplicationContent(), new TypeReference<Map<String, Object>>() {}));
      application.setApplicationReference(entity.getApplicationReference());
      application.setCreatedAt(OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC));
      application.setUpdatedAt(OffsetDateTime.ofInstant(entity.getUpdatedAt(), ZoneOffset.UTC));
      
      application.setIndividuals(
          Optional.ofNullable(entity.getIndividuals())
              .orElse(Set.of())
              .stream()
              .map(individualMapper::toIndividual)
              .filter(Objects::nonNull)
              .toList()
      );
      
      return application;
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to deserialize applicationContent from entity", e);
    }
  }

  /**
   * Converts a {@link Instant} timestamp to an {@link OffsetDateTime} using UTC as the default zone offset.
   *
   * @param instant the {@link Instant} to convert
   * @return the equivalent {@link OffsetDateTime}, or {@code null} if the input is null
   */
  default OffsetDateTime toOffsetDateTime(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}

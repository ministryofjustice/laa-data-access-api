package uk.gov.justice.laa.dstew.access.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
    entity.setStatus(req.getStatus());
    entity.setLaaReference(req.getLaaReference());
    var individuals = req.getIndividuals()
                         .stream()
                         .map(individualMapper::toIndividualEntity)
                         .collect(Collectors.toSet());
    entity.setIndividuals(individuals);
    entity.setApplicationContent(getApplicationContent(req.getApplicationContent(),
            "Failed to serialize ApplicationCreateRequest.applicationContent"));
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
      Map<String, Object> applicationContent = getApplicationContent(req.getApplicationContent(),
              "Failed to serialize ApplicationUpdateRequest.applicationContent");
      entity.setApplicationContent(
              applicationContent);
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
    Map<String, Object> applicationContent = getApplicationContent(entity.getApplicationContent(),
            "Failed to deserialize applicationContent from entity");

    Application application = new Application();
        application.setId(entity.getId());
        application.setApplicationStatus(entity.getStatus());
        application.setSchemaVersion(entity.getSchemaVersion());

        application.setApplicationContent(
                applicationContent);
        application.setLaaReference(entity.getLaaReference());
        application.caseworkerId(entity.getCaseworker() != null ? entity.getCaseworker().getId() : null);
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

    }

    //TODO why are we mapping a map to a map?
  /**
   * Helper method to safely convert a Map to application content.
   *
   * @param contentMap the map to convert
   * @param message the error message to use if conversion fails
   * @return the converted application content
   * @throws IllegalArgumentException if conversion fails
   */
  private static Map<String, Object> getApplicationContent(Map<String, Object> contentMap, String message) {
    Map<String, Object> applicationContent;
    try {
      applicationContent = objectMapper.convertValue(contentMap, new TypeReference<>() {
      });

    } catch (Exception e) {
      throw new IllegalArgumentException(
              message, e);
    }
    return applicationContent;
  }

}

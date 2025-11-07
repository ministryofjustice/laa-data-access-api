package uk.gov.justice.laa.dstew.access.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.uuid.Generators;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;

/**
 * Mapper between ApplicationEntity and DTOs.
 * Handles JSONB content for applicationContent.
 */
@Mapper(componentModel = "spring")
public interface ApplicationMapper {

  ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * Convert a create request into an ApplicationEntity, storing the content in JSONB.
   */
  default ApplicationEntity toApplicationEntity(ApplicationCreateRequest req) {
    if (req == null) {
      return null;
    }

    ApplicationEntity entity = new ApplicationEntity();
    entity.setId(Generators.timeBasedEpochGenerator().generate());
    entity.setStatusId(req.getStatusId());
    entity.setSchemaVersion(req.getSchemaVersion());

    try {
      // Store all fields inside applicationContent
      entity.setApplicationContent(OBJECT_MAPPER.convertValue(req.getApplicationContent(), Map.class));
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to serialize ApplicationCreateRequest.applicationContent", e);
    }

    return entity;
  }

  /**
   * Apply updates from an update request into an existing ApplicationEntity.
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  default void updateApplicationEntity(@MappingTarget ApplicationEntity entity, ApplicationUpdateRequest req) {
    if (req.getStatusId() != null) {
      entity.setStatusId(req.getStatusId());
    }
    if (req.getSchemaVersion() != null) {
      entity.setSchemaVersion(req.getSchemaVersion());
    }

    if (req.getApplicationContent() != null) {
      try {
        // Merge or replace content
        entity.setApplicationContent(OBJECT_MAPPER.convertValue(req.getApplicationContent(), Map.class));
      } catch (Exception e) {
        throw new IllegalArgumentException("Failed to serialize ApplicationUpdateRequest.applicationContent", e);
      }
    }
  }

  /**
   * Convert ApplicationEntity into an Application DTO, deserializing JSONB content.
   */
  default Application toApplication(ApplicationEntity entity) {
    if (entity == null) {
      return null;
    }
    try {
      Application app = new Application();
      app.setId(entity.getId());
      app.setApplicationStatus(entity.getStatusEntity().getCode());
      app.setSchemaVersion(entity.getSchemaVersion());

      // Deserialize JSONB content
      var contentMap = OBJECT_MAPPER.convertValue(
          entity.getApplicationContent(),
          new TypeReference<java.util.Map<String, Object>>() {
          }
      );
      app.setApplicationContent(contentMap);

      return app;
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to deserialize applicationContent from entity", e);
    }
  }

  default OffsetDateTime toOffsetDateTime(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}

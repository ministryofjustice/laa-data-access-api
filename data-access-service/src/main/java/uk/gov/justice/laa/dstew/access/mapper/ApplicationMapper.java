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

@Mapper(componentModel = "spring")
public interface ApplicationMapper {

  ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  default ApplicationEntity toApplicationEntity(ApplicationCreateRequest req) {
    if (req == null) return null;

    ApplicationEntity entity = new ApplicationEntity();
    entity.setId(Generators.timeBasedEpochGenerator().generate());
    entity.setStatus(req.getStatus());
    entity.setSchemaVersion(req.getSchemaVersion());

    try {
      entity.setApplicationContent(
          OBJECT_MAPPER.convertValue(req.getApplicationContent(), Map.class));
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Failed to serialize ApplicationCreateRequest.applicationContent", e);
    }
    return entity;
  }

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  default void updateApplicationEntity(@MappingTarget ApplicationEntity entity, ApplicationUpdateRequest req) {
    if (req.getStatus() != null) {
      entity.setStatus(req.getStatus());
    }
    if (req.getSchemaVersion() != null) {
      entity.setSchemaVersion(req.getSchemaVersion());
    }
    if (req.getApplicationContent() != null) {
      try {
        entity.setApplicationContent(
            OBJECT_MAPPER.convertValue(req.getApplicationContent(), Map.class));
      } catch (Exception e) {
        throw new IllegalArgumentException(
            "Failed to serialize ApplicationUpdateRequest.applicationContent", e);
      }
    }
  }

  default Application toApplication(ApplicationEntity entity) {
    if (entity == null) return null;
    try {
      Application app = new Application();
      app.setId(entity.getId());
      app.setApplicationStatus(entity.getStatus());
      app.setSchemaVersion(entity.getSchemaVersion());
      app.setApplicationContent(
          OBJECT_MAPPER.convertValue(entity.getApplicationContent(), new TypeReference<Map<String, Object>>() {}));
      return app;
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to deserialize applicationContent from entity", e);
    }
  }

  default OffsetDateTime toOffsetDateTime(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}

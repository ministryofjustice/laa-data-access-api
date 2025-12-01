package uk.gov.justice.laa.dstew.access.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;

/**
 * Mapper between ApplicationSummaryEntity and DTOs.
 * Handles JSONB content for applicationContent.
 */
@Mapper(componentModel = "spring")
public interface ApplicationSummaryMapper {

  /**
   * Maps the given application summary entity to an application summary.
   *
   * @param applicationSummaryEntity the application summary entity
   * @return the application summary
   */
  default ApplicationSummary toApplicationSummary(ApplicationSummaryEntity applicationSummaryEntity) {

    if (applicationSummaryEntity == null) {
      return null;
    }
    try {
      ApplicationSummary app = new ApplicationSummary();
      app.setApplicationId(applicationSummaryEntity.getId());
      app.setApplicationStatus(applicationSummaryEntity.getStatus());
      app.setApplicationReference(applicationSummaryEntity.getApplicationReference());
      app.setAssignedTo(applicationSummaryEntity.getCaseworker() != null 
                        ? 
                        applicationSummaryEntity.getCaseworker().getId() : 
                        null);
      app.setCreatedAt(applicationSummaryEntity.getCreatedAt().atOffset(ZoneOffset.UTC));
      app.setModifiedAt(applicationSummaryEntity.getModifiedAt().atOffset(ZoneOffset.UTC));
      return app;
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to deserialize applicationContent from entity", e);
    }
  }

  default OffsetDateTime map(Instant value) {
    return value != null ? value.atOffset(ZoneOffset.UTC) : null;
  }
}

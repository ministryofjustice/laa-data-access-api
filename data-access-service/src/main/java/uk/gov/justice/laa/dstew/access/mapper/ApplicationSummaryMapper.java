package uk.gov.justice.laa.dstew.access.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
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
  @Mapping(target = "applicationId", source = "id")
  @Mapping(target = "applicationStatus", source = "status")
  ApplicationSummary toApplicationSummary(ApplicationSummaryEntity applicationSummaryEntity);

  default OffsetDateTime map(Instant value) {
    return value != null ? value.atOffset(ZoneOffset.UTC) : null;
  }
}

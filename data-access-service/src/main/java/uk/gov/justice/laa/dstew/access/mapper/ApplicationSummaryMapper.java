package uk.gov.justice.laa.dstew.access.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryResponse;

/**
 * Mapper between ApplicationSummaryEntity and DTOs. Handles JSONB content for applicationContent.
 */
@Mapper(componentModel = "spring")
public interface ApplicationSummaryMapper {

  /**
   * Maps the given application summary DTO to an application summary.
   *
   * @param dto the application summary DTO
   * @return the application summary
   */
  default ApplicationSummary toApplicationSummary(ApplicationSummaryDto dto) {
    if (dto == null) {
      return null;
    }
    ApplicationSummary app = new ApplicationSummary();
    app.setApplicationId(dto.getId());
    app.setSubmittedAt(
        dto.getSubmittedAt() != null ? dto.getSubmittedAt().atOffset(ZoneOffset.UTC) : null);
    app.setAutoGrant(dto.getIsAutoGranted());
    app.setCategoryOfLaw(dto.getCategoryOfLaw());
    app.setMatterType(dto.getMatterType());
    app.setUsedDelegatedFunctions(dto.getUsedDelegatedFunctions());
    app.setLaaReference(dto.getLaaReference());
    app.setOfficeCode(dto.getOfficeCode());
    app.setStatus(dto.getStatus());
    app.setAssignedTo(dto.getCaseworkerId());
    app.setClientFirstName(dto.getClientFirstName());
    app.setClientLastName(dto.getClientLastName());
    app.setClientDateOfBirth(dto.getClientDateOfBirth());
    app.setApplicationType(ApplicationType.INITIAL);
    app.setLastUpdated(dto.getModifiedAt().atOffset(ZoneOffset.UTC));
    app.setIsLead(dto.isLead());
    return app;
  }

  default OffsetDateTime map(Instant value) {
    return value != null ? value.atOffset(ZoneOffset.UTC) : null;
  }

  /**
   * Maps the given linked application summary DTO to a linked application summary.
   *
   * @param dto the linked application summary DTO
   * @return the linked application summary
   */
  LinkedApplicationSummaryResponse toLinkedApplicationSummary(LinkedApplicationSummaryDto dto);
}

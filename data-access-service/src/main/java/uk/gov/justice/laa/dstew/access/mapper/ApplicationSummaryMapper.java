package uk.gov.justice.laa.dstew.access.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryResult;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryResponse;

/**
 * Mapper between ApplicationSummaryResult and DTOs.
 */
@Mapper(componentModel = "spring")
public interface ApplicationSummaryMapper {

  /**
   * Maps the given application summary result projection to an application summary.
   *
   * @param result the application summary result projection
   * @return the application summary
   */
  default ApplicationSummary toApplicationSummary(ApplicationSummaryResult result) {
    if (result == null) {
      return null;
    }
    ApplicationSummary app = new ApplicationSummary();
    app.setApplicationId(result.getId());
    app.setStatus(result.getStatus());
    app.setLaaReference(result.getLaaReference());
    app.setOfficeCode(result.getOfficeCode());
    app.setSubmittedAt(result.getSubmittedAt() != null
        ? result.getSubmittedAt().atOffset(ZoneOffset.UTC)
        : null);
    app.setLastUpdated(result.getModifiedAt() != null
        ? result.getModifiedAt().atOffset(ZoneOffset.UTC)
        : null);
    app.setUsedDelegatedFunctions(result.getUsedDelegatedFunctions());
    app.setCategoryOfLaw(result.getCategoryOfLaw());
    app.setMatterType(result.getMatterType());
    app.setAutoGrant(result.getIsAutoGranted());
    app.setIsLead(result.getIsLead());
    app.setAssignedTo(result.getCaseworkerId());
    app.setClientFirstName(result.getClientFirstName());
    app.setClientLastName(result.getClientLastName());
    app.setClientDateOfBirth(result.getClientDateOfBirth());
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

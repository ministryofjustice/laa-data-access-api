package uk.gov.justice.laa.dstew.access.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryView;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryResponse;

/**
 * Mapper between ApplicationSummaryEntity and DTOs.
 * Handles JSONB content for applicationContent.
 */
@Mapper(componentModel = "spring")
public interface ApplicationSummaryMapper {

  /**
   * Maps the given application summary entity to an application summary.
   *
   * @param applicationSummaryView the application summary view
   * @return the application summary
   */
  default ApplicationSummary toApplicationSummary(ApplicationSummaryView applicationSummaryView) {

    if (applicationSummaryView == null) {
      return null;
    }
    ApplicationSummary applicationSummary = new ApplicationSummary();
    applicationSummary.setApplicationId(applicationSummaryView.getId());
    applicationSummary.setSubmittedAt(
        applicationSummaryView.getSubmittedAt() != null ? applicationSummaryView.getSubmittedAt().atOffset(ZoneOffset.UTC) :
            null);
    applicationSummary.setAutoGrant(applicationSummaryView.getIsAutoGranted());
    applicationSummary.setCategoryOfLaw(applicationSummaryView.getCategoryOfLaw());
    applicationSummary.setMatterType(applicationSummaryView.getMatterType());
    applicationSummary.setUsedDelegatedFunctions(applicationSummaryView.getUsedDelegatedFunctions());
    applicationSummary.setLaaReference(applicationSummaryView.getLaaReference());
    applicationSummary.setOfficeCode(applicationSummaryView.getOfficeCode());
    applicationSummary.setStatus(applicationSummaryView.getStatus());
    applicationSummary.setAssignedTo(
        applicationSummaryView.getCaseworker() != null ? applicationSummaryView.getCaseworker().getId() : null);
    applicationSummary.setClientFirstName(applicationSummaryView.getIndividualsFirstName());
    applicationSummary.setClientLastName(applicationSummaryView.getIndividualsLastName());
    applicationSummary.setClientDateOfBirth(applicationSummaryView.getIndividualsDateOfBirth());
    applicationSummary.setApplicationType(ApplicationType.INITIAL);
    applicationSummary.setLastUpdated(
        applicationSummaryView.getModifiedAt() != null ? applicationSummaryView.getModifiedAt().atOffset(ZoneOffset.UTC) :
            null);
    return applicationSummary;
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

package uk.gov.justice.laa.dstew.access.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResult;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;

/**
 * Mapper between ApplicationSummaryResult and ApplicationSummary.
 */
@Mapper(componentModel = "spring")
public interface ApplicationSummaryMapper {

  default ApplicationSummary toApplicationSummary(ApplicationSummaryResult result) {
    if (result == null) {
      return null;
    }
    ApplicationSummary app = new ApplicationSummary();
    app.setApplicationId(result.id());
    app.setLaaReference(result.laaReference());
    app.setStatus(result.status());
    app.setSubmittedAt(result.submittedAt() != null
        ? result.submittedAt().atOffset(ZoneOffset.UTC)
        : null);
    app.setAutoGrant(result.isAutoGranted());
    app.setCategoryOfLaw(result.categoryOfLaw());
    app.setMatterType(result.matterType());
    app.setUsedDelegatedFunctions(result.usedDelegatedFunctions());
    app.setOfficeCode(result.officeCode());
    app.setAssignedTo(result.caseworkerId());
    app.setClientFirstName(result.clientFirstName());
    app.setClientLastName(result.clientLastName());
    app.setClientDateOfBirth(result.clientDateOfBirth());
    app.setLastUpdated(result.modifiedAt().atOffset(ZoneOffset.UTC));
    app.setApplicationType(ApplicationType.INITIAL);
    return app;
  }

  default OffsetDateTime map(Instant value) {
    return value != null ? value.atOffset(ZoneOffset.UTC) : null;
  }
}
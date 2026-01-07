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
      app.setSubmittedAt(applicationSummaryEntity.getSubmittedAt() != null 
                          ? applicationSummaryEntity.getSubmittedAt().atOffset(ZoneOffset.UTC) 
                          : null);
      app.setAutoGrant(applicationSummaryEntity.isAutoGranted());
      app.setCategoryOfLaw(applicationSummaryEntity.getCategoryOfLaw());
      app.setMatterType(applicationSummaryEntity.getMatterType());
      app.setUsedDelegatedFunctions(applicationSummaryEntity.isUsedDelegatedFunctions());
      app.setLaaReference(applicationSummaryEntity.getLaaReference());
      app.setApplicationStatus(applicationSummaryEntity.getStatus());
      app.setAssignedTo(applicationSummaryEntity.getCaseworker() != null 
                        ? 
                        applicationSummaryEntity.getCaseworker().getId() : 
                        null);
      var individual = applicationSummaryEntity.getIndividuals().stream().findFirst();
      individual.ifPresent(client -> {
        app.setClientFirstName(client.getFirstName());
        app.setClientLastName(client.getLastName());
        app.setClientDateOfBirth(client.getDateOfBirth());
      });
      app.setApplicationType(applicationSummaryEntity.getType());
      app.setLastUpdated(applicationSummaryEntity.getModifiedAt().atOffset(ZoneOffset.UTC));
      return app;
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to deserialize applicationContent from entity", e);
    }
  }
}

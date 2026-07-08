package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.PagingResponse;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.GetAllApplicationsResult;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.model.ApplicationSummaryReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.model.LinkedApplicationSummaryReadModel;

/**
 * Maps a {@link GetAllApplicationsResult} to a {@link ResponseEntity} containing an {@link
 * ApplicationSummaryResponse}.
 */
public class GetAllApplicationsResponseMapper {

  /**
   * Maps the result to a response entity.
   *
   * @param result the use-case result
   * @return the application summary response
   */
  public ResponseEntity<ApplicationSummaryResponse> toGetAllApplicationsResponse(
      GetAllApplicationsResult result) {
    List<ApplicationSummary> applications =
        result.applications().content().stream().map(this::toApplicationSummary).toList();

    PagingResponse pagingResponse = new PagingResponse();
    pagingResponse.setPage(result.requestedPage());
    pagingResponse.pageSize(result.requestedPageSize());
    pagingResponse.totalRecords((int) result.applications().totalElements());
    pagingResponse.itemsReturned(applications.size());

    ApplicationSummaryResponse response = new ApplicationSummaryResponse();
    response.setApplications(applications);
    response.setPaging(pagingResponse);

    return ResponseEntity.ok(response);
  }

  private ApplicationSummary toApplicationSummary(ApplicationSummaryReadModel domain) {
    ApplicationSummary app = new ApplicationSummary();
    app.setApplicationId(domain.id());
    app.setSubmittedAt(
        domain.submittedAt() != null ? domain.submittedAt().atOffset(ZoneOffset.UTC) : null);
    app.setAutoGrant(domain.isAutoGranted());
    app.setCategoryOfLaw(
        domain.categoryOfLaw() != null ? CategoryOfLaw.valueOf(domain.categoryOfLaw()) : null);
    app.setMatterType(domain.matterType() != null ? MatterType.valueOf(domain.matterType()) : null);
    app.setUsedDelegatedFunctions(domain.usedDelegatedFunctions());
    app.setLaaReference(domain.laaReference());
    app.setOfficeCode(domain.officeCode());
    app.setStatus(domain.status() != null ? ApplicationStatus.valueOf(domain.status()) : null);
    app.setAssignedTo(domain.caseworkerId());
    app.setClientFirstName(domain.clientFirstName());
    app.setClientLastName(domain.clientLastName());
    app.setClientDateOfBirth(domain.clientDateOfBirth());
    app.setApplicationType(ApplicationType.INITIAL);
    app.setLastUpdated(domain.modifiedAt().atOffset(ZoneOffset.UTC));
    app.setIsLead(domain.isLead());
    app.setLinkedApplications(
        domain.linkedApplications().stream()
            .map(this::toLinkedApplicationSummaryResponse)
            .toList());
    return app;
  }

  private LinkedApplicationSummaryResponse toLinkedApplicationSummaryResponse(
      LinkedApplicationSummaryReadModel domain) {
    LinkedApplicationSummaryResponse response = new LinkedApplicationSummaryResponse();
    response.setApplicationId(domain.applicationId());
    response.setLaaReference(domain.laaReference());
    response.setIsLead(domain.isLead());
    return response;
  }
}

package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.domain.ApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.domain.LinkedApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.PagingResponse;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.GetAllApplicationsResult;

/**
 * Maps a {@link GetAllApplicationsResult} to a {@link ResponseEntity} containing an {@link
 * ApplicationSummaryResponse}. Responsible for constructing both the application list and the
 * paging envelope — no presentation logic resides in the controller.
 */
public class GetAllApplicationsResponseMapper {

  /**
   * Converts the use-case result to an HTTP 200 response containing the full application summary
   * and paging metadata.
   *
   * @param result the use-case result
   * @return response entity containing the application summary response
   */
  public ResponseEntity<ApplicationSummaryResponse> toGetAllApplicationsResponse(
      GetAllApplicationsResult result) {
    List<ApplicationSummary> applications =
        result.applications().getContent().stream().map(this::toApplicationSummary).toList();

    PagingResponse pagingResponse = new PagingResponse();
    pagingResponse.setPage(result.requestedPage());
    pagingResponse.pageSize(result.requestedPageSize());
    pagingResponse.totalRecords((int) result.applications().getTotalElements());
    pagingResponse.itemsReturned(applications.size());

    ApplicationSummaryResponse response = new ApplicationSummaryResponse();
    response.setApplications(applications);
    response.setPaging(pagingResponse);

    return ResponseEntity.ok(response);
  }

  private ApplicationSummary toApplicationSummary(ApplicationSummaryDomain domain) {
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
      LinkedApplicationSummaryDomain domain) {
    LinkedApplicationSummaryResponse response = new LinkedApplicationSummaryResponse();
    response.setApplicationId(domain.applicationId());
    response.setLaaReference(domain.laaReference());
    response.setIsLead(domain.isLead());
    return response;
  }
}

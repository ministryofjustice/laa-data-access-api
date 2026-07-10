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

  private ApplicationSummary toApplicationSummary(ApplicationSummaryReadModel summaryReadModel) {
    ApplicationSummary app = new ApplicationSummary();
    app.setApplicationId(summaryReadModel.id());
    app.setSubmittedAt(
        summaryReadModel.submittedAt() != null
            ? summaryReadModel.submittedAt().atOffset(ZoneOffset.UTC)
            : null);
    app.setAutoGrant(summaryReadModel.isAutoGranted());
    app.setCategoryOfLaw(
        summaryReadModel.categoryOfLaw() != null
            ? CategoryOfLaw.valueOf(summaryReadModel.categoryOfLaw())
            : null);
    app.setMatterType(
        summaryReadModel.matterType() != null
            ? MatterType.valueOf(summaryReadModel.matterType())
            : null);
    app.setUsedDelegatedFunctions(summaryReadModel.usedDelegatedFunctions());
    app.setLaaReference(summaryReadModel.laaReference());
    app.setOfficeCode(summaryReadModel.officeCode());
    app.setStatus(
        summaryReadModel.status() != null
            ? ApplicationStatus.valueOf(summaryReadModel.status())
            : null);
    app.setAssignedTo(summaryReadModel.caseworkerId());
    app.setClientFirstName(summaryReadModel.clientFirstName());
    app.setClientLastName(summaryReadModel.clientLastName());
    app.setClientDateOfBirth(summaryReadModel.clientDateOfBirth());
    app.setApplicationType(ApplicationType.INITIAL);
    app.setLastUpdated(summaryReadModel.modifiedAt().atOffset(ZoneOffset.UTC));
    app.setIsLead(summaryReadModel.isLead());
    app.setLinkedApplications(
        summaryReadModel.linkedApplications().stream()
            .map(this::toLinkedApplicationSummaryResponse)
            .toList());
    return app;
  }

  private LinkedApplicationSummaryResponse toLinkedApplicationSummaryResponse(
      LinkedApplicationSummaryReadModel summaryReadModel) {
    LinkedApplicationSummaryResponse response = new LinkedApplicationSummaryResponse();
    response.setApplicationId(summaryReadModel.applicationId());
    response.setLaaReference(summaryReadModel.laaReference());
    response.setIsLead(summaryReadModel.isLead());
    return response;
  }
}

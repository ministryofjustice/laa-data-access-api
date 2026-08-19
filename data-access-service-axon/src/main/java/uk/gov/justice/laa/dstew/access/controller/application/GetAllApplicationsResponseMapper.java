package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationClient;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.PagingResponse;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsResult;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;

/** Maps a {@link FindAllApplicationsResult} to an {@link ApplicationSummaryResponse}. */
@Component
public class GetAllApplicationsResponseMapper {

  /** Builds the paginated response from the query result. */
  public ResponseEntity<ApplicationSummaryResponse> toResponse(FindAllApplicationsResult result) {
    List<ApplicationSummary> summaries =
        result.applications().stream().map(app -> toSummary(app, result.groupsByLeadId())).toList();

    PagingResponse paging = new PagingResponse();
    paging.setPage(result.requestedPage());
    paging.pageSize(result.requestedPageSize());
    paging.totalRecords((int) result.totalElements());
    paging.itemsReturned(summaries.size());

    ApplicationSummaryResponse response = new ApplicationSummaryResponse();
    response.setApplications(summaries);
    response.setPaging(paging);

    return ResponseEntity.ok(response);
  }

  private ApplicationSummary toSummary(
      ApplicationReadModel app, Map<UUID, LinkedApplicationGroupReadModel> groupsByLeadId) {
    ApplicationSummary summary = new ApplicationSummary();
    summary.setApplicationId(app.getApplicationId());
    summary.setStatus(app.getStatus() != null ? ApplicationStatus.valueOf(app.getStatus()) : null);
    summary.setLaaReference(app.getLaaReference());
    summary.setOfficeCode(app.getOfficeCode());
    summary.setUsedDelegatedFunctions(app.getUsedDelegatedFunctions());
    summary.setCategoryOfLaw(toCategoryOfLaw(app.getCategoryOfLaw()));
    summary.setMatterType(toMatterType(app.getMatterType()));
    summary.setSubmittedAt(
        app.getSubmittedAt() != null ? app.getSubmittedAt().atOffset(ZoneOffset.UTC) : null);
    summary.setLastUpdated(app.getModifiedAt().atOffset(ZoneOffset.UTC));
    // Linked groups are not yet exposed; always false until the grouping endpoint is available.
    summary.setIsLead(false);
    summary.setAssignedTo(app.getCaseworkerId());
    summary.setAutoGranted(
        uk.gov.justice.laa.dstew.access.model.AutoGranted.valueOf(app.getAutoGranted().name()));

    populateClientDetails(summary, app);

    summary.setLinkedApplications(toLinkedSummaries(app, groupsByLeadId));
    return summary;
  }

  private CategoryOfLaw toCategoryOfLaw(String categoryOfLaw) {
    if (categoryOfLaw == null) {
      return null;
    }
    try {
      return CategoryOfLaw.valueOf(categoryOfLaw.toUpperCase().replace(" ", "_"));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private MatterType toMatterType(String matterType) {
    if (matterType == null) {
      return null;
    }
    try {
      return MatterType.valueOf(matterType.toUpperCase().replace(" ", "_"));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private void populateClientDetails(ApplicationSummary summary, ApplicationReadModel app) {
    ApplicationClient client = app.getClient();
    if (client != null) {
      summary.setClientFirstName(client.getFirstName());
      summary.setClientLastName(client.getLastName());
      if (client.getDateOfBirth() != null) {
        summary.setClientDateOfBirth(client.getDateOfBirth());
      }
    }
  }

  private List<LinkedApplicationSummaryResponse> toLinkedSummaries(
      ApplicationReadModel app, Map<UUID, LinkedApplicationGroupReadModel> groupsByLeadId) {
    UUID effectiveLeadId =
        app.getLeadApplicationId() != null ? app.getLeadApplicationId() : app.getApplicationId();
    LinkedApplicationGroupReadModel group = groupsByLeadId.get(effectiveLeadId);
    if (group == null) {
      return Collections.emptyList();
    }
    return group.getMemberIds().stream()
        .filter(memberId -> !memberId.equals(app.getApplicationId()))
        .map(
            memberId -> {
              LinkedApplicationSummaryResponse linked = new LinkedApplicationSummaryResponse();
              linked.setApplicationId(memberId);
              linked.setIsLead(memberId.equals(group.getLeadApplicationId()));
              return linked;
            })
        .toList();
  }
}

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
import uk.gov.justice.laa.dstew.access.model.AutoGranted;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.PagingResponse;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsResult;
import uk.gov.justice.laa.dstew.access.query.application.LinkedApplicationMemberDetails;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;

/** Maps a {@link FindAllApplicationsResult} to an {@link ApplicationSummaryResponse}. */
@Component
public class GetAllApplicationsResponseMapper {

  /** Builds the paginated response from the query result. */
  public ResponseEntity<ApplicationSummaryResponse> toResponse(FindAllApplicationsResult result) {
    List<ApplicationSummary> summaries =
        result.applications().stream().map(app -> toSummary(app, result)).toList();

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

  private ApplicationSummary toSummary(ApplicationReadModel app, FindAllApplicationsResult result) {
    var group =
        app.getLinkedGroupId() == null
            ? null
            : result.groupsByGroupId().get(app.getLinkedGroupId());
    ApplicationSummary summary = new ApplicationSummary();
    summary.setApplicationId(app.getApplicationId());
    summary.setStatus(app.getStatus() != null ? ApplicationStatus.valueOf(app.getStatus()) : null);
    summary.setLaaReference(app.getLaaReference());
    summary.setOfficeCode(app.getOfficeCode());
    summary.setUsedDelegatedFunctions(app.getUsedDelegatedFunctions());
    summary.setCategoryOfLaw(app.getCategoryOfLaw());
    summary.setCategoryOfLawCode(app.getCategoryOfLawCode());
    summary.setMatterType(app.getMatterType());
    summary.setMatterTypeCode(app.getMatterTypeCode());
    summary.setSubmittedAt(
        app.getSubmittedAt() != null ? app.getSubmittedAt().atOffset(ZoneOffset.UTC) : null);
    summary.setLastUpdated(app.getModifiedAt().atOffset(ZoneOffset.UTC));
    summary.setIsLead(group != null && app.getApplicationId().equals(group.getLeadApplicationId()));
    summary.setLinkedGroupVersion(LinkedGroupVersionTokens.encode(group));
    summary.setAssignedTo(app.getCaseworkerId());
    summary.setAutoGranted(AutoGranted.valueOf(app.getAutoGranted().name()));

    populateClientDetails(summary, app);

    summary.setLinkedApplications(toLinkedSummaries(app, group, result.linkedMemberDetails()));
    summary.setPriorAuthorities(
        PriorAuthoritySummaryMapper.toSummaries(
            result
                .priorAuthoritiesByApplicationId()
                .getOrDefault(app.getApplicationId(), List.of())));
    return summary;
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
      ApplicationReadModel app,
      LinkedApplicationGroupReadModel group,
      Map<UUID, LinkedApplicationMemberDetails> linkedMemberDetails) {
    if (group == null) {
      return Collections.emptyList();
    }
    return group.getMemberIds().stream()
        .filter(memberId -> !memberId.equals(app.getApplicationId()))
        .map(
            memberId -> {
              LinkedApplicationMemberDetails member = linkedMemberDetails.get(memberId);
              if (member == null) {
                throw new IllegalStateException(
                    "Linked application details are missing for " + memberId);
              }
              LinkedApplicationSummaryResponse linked = new LinkedApplicationSummaryResponse();
              linked.setApplicationId(memberId);
              linked.setLaaReference(member.laaReference());
              linked.setIsLead(memberId.equals(group.getLeadApplicationId()));
              linked.setClientFirstName(member.clientFirstName());
              linked.setClientLastName(member.clientLastName());
              return linked;
            })
        .toList();
  }
}

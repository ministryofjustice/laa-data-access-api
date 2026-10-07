package uk.gov.justice.laa.dstew.access.controller.worklist;

import java.time.ZoneOffset;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.PagingResponse;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.model.WorkListItem;
import uk.gov.justice.laa.dstew.access.model.WorkListItemType;
import uk.gov.justice.laa.dstew.access.model.WorkListResponse;
import uk.gov.justice.laa.dstew.access.query.worklist.FindWorkListItemsResult;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListItemReadModel;

/** Maps active work-list projection rows to the public work-list contract. */
@Component
public class WorkListResponseMapper {

  /** Maps one database-paged query result without consulting command-side routes. */
  public ResponseEntity<WorkListResponse> toResponse(FindWorkListItemsResult result) {
    WorkListResponse response = new WorkListResponse();
    response.setItems(result.items().stream().map(this::toItem).toList());

    PagingResponse paging = new PagingResponse();
    paging.setPage(result.requestedPage());
    paging.setPageSize(result.requestedPageSize());
    paging.setItemsReturned(result.items().size());
    paging.setTotalRecords(Math.toIntExact(result.totalElements()));
    response.setPaging(paging);
    return ResponseEntity.ok(response);
  }

  private WorkListItem toItem(WorkListItemReadModel item) {
    WorkListItem response = new WorkListItem();
    response.setItemId(item.getId());
    response.setItemType(WorkListItemType.valueOf(item.getItemType().name()));
    response.setParentApplicationId(item.getParentApplicationId());
    response.setAssignedTo(item.getAssigneeId());
    response.setAssignmentVersion(item.getAssignmentVersion());
    response.setAssignmentBoundaryType(
        WorkListItem.AssignmentBoundaryTypeEnum.valueOf(item.getAssignmentBoundaryType()));
    response.setReadyAt(item.getReadyAt().atOffset(ZoneOffset.UTC));
    response.setLaaReference(item.getLaaReference());
    response.setUsedDelegatedFunctions(item.getUsedDelegatedFunctions());
    boolean priorAuthority = item.getItemType() == WorkItemType.PRIOR_AUTHORITY;
    response.setCategoryOfLaw(priorAuthority ? null : item.getCategoryOfLaw());
    response.setCategoryOfLawCode(priorAuthority ? null : item.getCategoryOfLawCode());
    response.setMatterTypes(item.getMatterTypes());
    response.setMatterTypeCodes(item.getMatterTypeCodes());
    response.setApplicationStatus(
        item.getApplicationStatus() == null
            ? null
            : ApplicationStatus.valueOf(item.getApplicationStatus()));
    response.setPriorAuthorityType(
        item.getPriorAuthorityType() == null
            ? null
            : PriorAuthorityType.valueOf(item.getPriorAuthorityType()));
    response.setExpertType(item.getExpertType());
    return response;
  }
}

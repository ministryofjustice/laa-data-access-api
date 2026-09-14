package uk.gov.justice.laa.dstew.access.controller.worklist;

import java.time.ZoneOffset;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.PagingResponse;
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
    response.setSubmittedAt(item.getSubmittedAt().atOffset(ZoneOffset.UTC));
    response.setLaaReference(item.getLaaReference());
    response.setUsedDelegatedFunctions(item.getUsedDelegatedFunctions());
    response.setCategoryOfLaw(toCategoryOfLaw(item.getCategoryOfLaw()));
    response.setMatterTypes(
        item.getMatterTypes() == null
            ? null
            : item.getMatterTypes().stream().map(this::toMatterType).toList());
    response.setApplicationStatus(
        item.getApplicationStatus() == null
            ? null
            : ApplicationStatus.valueOf(item.getApplicationStatus()));
    return response;
  }

  private CategoryOfLaw toCategoryOfLaw(String categoryOfLaw) {
    return categoryOfLaw == null
        ? null
        : CategoryOfLaw.valueOf(categoryOfLaw.toUpperCase().replace(" ", "_"));
  }

  private MatterType toMatterType(String matterType) {
    return MatterType.valueOf(matterType.toUpperCase().replace(" ", "_"));
  }
}

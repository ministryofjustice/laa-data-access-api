package uk.gov.justice.laa.dstew.access.controller.worklist;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.WorkListQueryApi;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.model.WorkListItemType;
import uk.gov.justice.laa.dstew.access.model.WorkListResponse;
import uk.gov.justice.laa.dstew.access.query.worklist.FindWorkListItemsQuery;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedUserId;
import uk.gov.justice.laa.dstew.access.usecase.worklist.WorkListQueryUseCase;

/** HTTP adapter for the replayable work-list projection. */
@RestController
public class WorkListQueryController implements WorkListQueryApi {

  private final WorkListQueryUseCase workListQueryUseCase;
  private final WorkListResponseMapper responseMapper;
  private final AuthenticatedUserId authenticatedUserId;

  /** Creates the HTTP adapter with its query use case, response mapper, and identity resolver. */
  public WorkListQueryController(
      WorkListQueryUseCase workListQueryUseCase,
      WorkListResponseMapper responseMapper,
      AuthenticatedUserId authenticatedUserId) {
    this.workListQueryUseCase = workListQueryUseCase;
    this.responseMapper = responseMapper;
    this.authenticatedUserId = authenticatedUserId;
  }

  /** Returns open applications by default, the personal queue, or both. */
  @Override
  public ResponseEntity<WorkListResponse> getWorkListItems(
      @RequestHeader("X-Service-Name") ServiceName serviceName,
      Boolean assignedToMe,
      Boolean unassigned,
      WorkListItemType itemType,
      Integer page,
      Integer pageSize) {
    return responseMapper.toResponse(
        workListQueryUseCase.findItems(
            new FindWorkListItemsQuery(
                assignedToMe,
                itemType == null ? null : WorkItemType.valueOf(itemType.name()),
                unassigned,
                page,
                pageSize,
                Boolean.TRUE.equals(assignedToMe) ? authenticatedUserId.get() : null)));
  }
}

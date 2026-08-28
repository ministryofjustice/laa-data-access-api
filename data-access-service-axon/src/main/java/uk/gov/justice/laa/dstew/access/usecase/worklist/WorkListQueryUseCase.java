package uk.gov.justice.laa.dstew.access.usecase.worklist;

import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.query.worklist.FindWorkListItemsQuery;
import uk.gov.justice.laa.dstew.access.query.worklist.FindWorkListItemsResult;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Secured use case for querying the replayable work-list projection. */
@Service
public class WorkListQueryUseCase {

  private final QueryGateway queryGateway;

  public WorkListQueryUseCase(QueryGateway queryGateway) {
    this.queryGateway = queryGateway;
  }

  /** Returns a server-paged active-work view. */
  @AllowApiCaseworker
  public FindWorkListItemsResult findItems(FindWorkListItemsQuery query) {
    return queryGateway.query(query, FindWorkListItemsResult.class).join();
  }
}


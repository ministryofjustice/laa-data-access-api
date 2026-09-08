package uk.gov.justice.laa.dstew.dataaccesstools.cli.priorauthorities;

import java.util.ArrayList;
import java.util.UUID;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.client.DataAccessApiClient;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.workflow.WorkflowResult;

public final class PriorAuthorityCreationWorkflow {
  private final DataAccessApiClient client;
  private final PriorAuthorityRequestFactory requestFactory;

  public PriorAuthorityCreationWorkflow(
      DataAccessApiClient client, PriorAuthorityRequestFactory requestFactory) {
    this.client = client;
    this.requestFactory = requestFactory;
  }

  public WorkflowResult createAll(UUID applicationId) {
    var results = new ArrayList<WorkflowResult.ItemResult>();
    for (PriorAuthorityRequestFactory.PriorAuthorityRequest request : requestFactory.createAll()) {
      try {
        var submissionId = client.createPriorAuthority(applicationId, request.request());
        results.add(new WorkflowResult.ItemResult(request.type(), true, "created", submissionId));
      } catch (RuntimeException exception) {
        results.add(
            new WorkflowResult.ItemResult(request.type(), false, exception.getMessage(), null));
        break;
      }
    }
    return new WorkflowResult(results);
  }
}

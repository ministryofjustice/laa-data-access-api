package uk.gov.justice.laa.dstew.dataaccesstools.utils.workflow;

import java.util.List;
import java.util.UUID;

public record WorkflowResult(List<ItemResult> items) {
  public boolean succeeded() {
    return items.stream().allMatch(ItemResult::succeeded);
  }

  public record ItemResult(
      String identifier,
      boolean succeeded,
      String detail,
      UUID applicationId,
      UUID priorAuthorityId,
      String state) {}
}

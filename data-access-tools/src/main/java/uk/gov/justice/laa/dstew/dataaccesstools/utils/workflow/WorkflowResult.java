package uk.gov.justice.laa.dstew.dataaccesstools.utils.workflow;

import java.util.List;

public record WorkflowResult(List<ItemResult> items) {
  public boolean succeeded() {
    return items.stream().allMatch(ItemResult::succeeded);
  }

  public record ItemResult(
      String identifier, boolean succeeded, String detail, java.util.UUID priorAuthorityId) {}
}

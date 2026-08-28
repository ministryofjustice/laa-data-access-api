package uk.gov.justice.laa.dstew.access.command.worklist.route;

import java.io.Serializable;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

/** Composite JPA identity for a command-side work-item route. */
@NoArgsConstructor
@EqualsAndHashCode
public class WorkItemRouteId implements Serializable {
  WorkItemType workItemType;
  UUID workItemId;

  public WorkItemRouteId(WorkItemType workItemType, UUID workItemId) {
    this.workItemType = workItemType;
    this.workItemId = workItemId;
  }
}

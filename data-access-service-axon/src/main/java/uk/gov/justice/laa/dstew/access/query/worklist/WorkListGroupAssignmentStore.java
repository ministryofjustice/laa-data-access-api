package uk.gov.justice.laa.dstew.access.query.worklist;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.groupassignment.JpaGroupAssignmentTrackingStore;

/** WorkListProjection's group-assignment tracking tables; reset with the projection. */
@Component
public class WorkListGroupAssignmentStore
    extends JpaGroupAssignmentTrackingStore<
        WorkListAssignmentGroupRow, WorkListAssignmentApplicationRow> {
  public WorkListGroupAssignmentStore(
      WorkListAssignmentGroupRowRepository groupRows,
      WorkListAssignmentApplicationRowRepository applicationRows) {
    super(groupRows, applicationRows);
  }

  @Override
  protected WorkListAssignmentGroupRow newGroupRow() {
    return new WorkListAssignmentGroupRow();
  }

  @Override
  protected WorkListAssignmentApplicationRow newApplicationRow() {
    return new WorkListAssignmentApplicationRow();
  }
}

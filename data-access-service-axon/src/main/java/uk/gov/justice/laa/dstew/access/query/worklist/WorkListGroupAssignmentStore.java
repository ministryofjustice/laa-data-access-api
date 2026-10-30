package uk.gov.justice.laa.dstew.access.query.worklist;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.groupassignment.JpaGroupAssignmentTrackingStore;

/** WorkListProjection's group-assignment tracking table; reset with the projection. */
@Component
public class WorkListGroupAssignmentStore
    extends JpaGroupAssignmentTrackingStore<WorkListAssignmentApplicationRow> {
  public WorkListGroupAssignmentStore(WorkListAssignmentApplicationRowRepository applicationRows) {
    super(applicationRows);
  }

  @Override
  protected WorkListAssignmentApplicationRow newApplicationRow() {
    return new WorkListAssignmentApplicationRow();
  }
}

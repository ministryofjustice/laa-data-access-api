package uk.gov.justice.laa.dstew.access.query.application.listindex;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.groupassignment.JpaGroupAssignmentTrackingStore;

/** ApplicationListIndexProjection's group-assignment tracking tables; reset with the projection. */
@Component
public class ApplicationListIndexGroupAssignmentStore
    extends JpaGroupAssignmentTrackingStore<
        ApplicationListIndexAssignmentGroupRow, ApplicationListIndexAssignmentApplicationRow> {
  public ApplicationListIndexGroupAssignmentStore(
      ApplicationListIndexAssignmentGroupRowRepository groupRows,
      ApplicationListIndexAssignmentApplicationRowRepository applicationRows) {
    super(groupRows, applicationRows);
  }

  @Override
  protected ApplicationListIndexAssignmentGroupRow newGroupRow() {
    return new ApplicationListIndexAssignmentGroupRow();
  }

  @Override
  protected ApplicationListIndexAssignmentApplicationRow newApplicationRow() {
    return new ApplicationListIndexAssignmentApplicationRow();
  }
}

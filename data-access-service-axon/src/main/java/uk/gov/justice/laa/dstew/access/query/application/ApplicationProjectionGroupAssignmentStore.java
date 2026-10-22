package uk.gov.justice.laa.dstew.access.query.application;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.groupassignment.JpaGroupAssignmentTrackingStore;

/** ApplicationProjection's group-assignment tracking tables; reset with the projection. */
@Component
public class ApplicationProjectionGroupAssignmentStore
    extends JpaGroupAssignmentTrackingStore<
        ApplicationProjectionAssignmentGroupRow, ApplicationProjectionAssignmentApplicationRow> {
  public ApplicationProjectionGroupAssignmentStore(
      ApplicationProjectionAssignmentGroupRowRepository groupRows,
      ApplicationProjectionAssignmentApplicationRowRepository applicationRows) {
    super(groupRows, applicationRows);
  }

  @Override
  protected ApplicationProjectionAssignmentGroupRow newGroupRow() {
    return new ApplicationProjectionAssignmentGroupRow();
  }

  @Override
  protected ApplicationProjectionAssignmentApplicationRow newApplicationRow() {
    return new ApplicationProjectionAssignmentApplicationRow();
  }
}

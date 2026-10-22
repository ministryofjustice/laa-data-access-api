package uk.gov.justice.laa.dstew.access.query.application.history;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.groupassignment.JpaGroupAssignmentTrackingStore;

/** ApplicationHistoryProjection's group-assignment tracking tables; reset with the projection. */
@Component
public class ApplicationHistoryGroupAssignmentStore
    extends JpaGroupAssignmentTrackingStore<
        ApplicationHistoryAssignmentGroupRow, ApplicationHistoryAssignmentApplicationRow> {
  public ApplicationHistoryGroupAssignmentStore(
      ApplicationHistoryAssignmentGroupRowRepository groupRows,
      ApplicationHistoryAssignmentApplicationRowRepository applicationRows) {
    super(groupRows, applicationRows);
  }

  @Override
  protected ApplicationHistoryAssignmentGroupRow newGroupRow() {
    return new ApplicationHistoryAssignmentGroupRow();
  }

  @Override
  protected ApplicationHistoryAssignmentApplicationRow newApplicationRow() {
    return new ApplicationHistoryAssignmentApplicationRow();
  }
}

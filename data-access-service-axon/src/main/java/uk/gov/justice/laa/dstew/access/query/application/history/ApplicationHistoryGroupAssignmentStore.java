package uk.gov.justice.laa.dstew.access.query.application.history;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.groupassignment.JpaGroupAssignmentTrackingStore;

/** ApplicationHistoryProjection's group-assignment tracking table; reset with the projection. */
@Component
public class ApplicationHistoryGroupAssignmentStore
    extends JpaGroupAssignmentTrackingStore<ApplicationHistoryAssignmentApplicationRow> {
  public ApplicationHistoryGroupAssignmentStore(
      ApplicationHistoryAssignmentApplicationRowRepository applicationRows) {
    super(applicationRows);
  }

  @Override
  protected ApplicationHistoryAssignmentApplicationRow newApplicationRow() {
    return new ApplicationHistoryAssignmentApplicationRow();
  }
}

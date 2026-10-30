package uk.gov.justice.laa.dstew.access.query.application.listindex;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.groupassignment.JpaGroupAssignmentTrackingStore;

/** ApplicationListIndexProjection's group-assignment tracking table; reset with the projection. */
@Component
public class ApplicationListIndexGroupAssignmentStore
    extends JpaGroupAssignmentTrackingStore<ApplicationListIndexAssignmentApplicationRow> {
  public ApplicationListIndexGroupAssignmentStore(
      ApplicationListIndexAssignmentApplicationRowRepository applicationRows) {
    super(applicationRows);
  }

  @Override
  protected ApplicationListIndexAssignmentApplicationRow newApplicationRow() {
    return new ApplicationListIndexAssignmentApplicationRow();
  }
}

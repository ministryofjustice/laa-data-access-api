package uk.gov.justice.laa.dstew.access.query.application;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.query.groupassignment.JpaGroupAssignmentTrackingStore;

/** ApplicationProjection's group-assignment tracking table; reset with the projection. */
@Component
public class ApplicationProjectionGroupAssignmentStore
    extends JpaGroupAssignmentTrackingStore<ApplicationProjectionAssignmentApplicationRow> {
  public ApplicationProjectionGroupAssignmentStore(
      ApplicationProjectionAssignmentApplicationRowRepository applicationRows) {
    super(applicationRows);
  }

  @Override
  protected ApplicationProjectionAssignmentApplicationRow newApplicationRow() {
    return new ApplicationProjectionAssignmentApplicationRow();
  }
}

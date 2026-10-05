package uk.gov.justice.laa.dstew.dataaccesstools.utils.client;

import java.util.UUID;

public interface DataAccessApiClient {
  default UUID createApplicationDraft(String requestBody) {
    throw new UnsupportedOperationException();
  }

  default UUID submitApplicationDraft(UUID applicationId) {
    throw new UnsupportedOperationException();
  }

  default void awaitApplicationReadable(UUID applicationId) {
    throw new UnsupportedOperationException();
  }

  default ApplicationDecisionData getApplicationDecisionData(UUID applicationId) {
    throw new UnsupportedOperationException();
  }

  void recordManualOutcome(UUID applicationId);

  default void recordAutograntedOutcome(UUID applicationId, String requestBody) {
    throw new UnsupportedOperationException();
  }

  void makeDecision(UUID applicationId, String requestBody);

  UUID createPriorAuthorityDraft(String requestBody);

  void updatePriorAuthorityDraft(UUID priorAuthorityId, String requestBody);

  UUID submitPriorAuthorityDraft(UUID priorAuthorityId);

  default void assignWorkListItem(
      UUID itemId, long expectedAssignmentVersion, String eventDescription) {
    throw new UnsupportedOperationException();
  }
}

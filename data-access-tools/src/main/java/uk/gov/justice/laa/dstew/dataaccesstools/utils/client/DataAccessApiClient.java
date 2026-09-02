package uk.gov.justice.laa.dstew.dataaccesstools.utils.client;

import java.util.UUID;

public interface DataAccessApiClient {
  UUID createApplication(String requestBody);

  void recordManualOutcome(UUID applicationId);

  void makeDecision(UUID applicationId, String requestBody);

  UUID createPriorAuthority(UUID applicationId, String requestBody);
}

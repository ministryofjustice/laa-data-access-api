package uk.gov.justice.laa.dstew.dataaccesstools.cli.priorauthorities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.client.DataAccessApiClient;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.workflow.WorkflowResult;

class PriorAuthorityCreationWorkflowTest {
  @Test
  void createsAllPriorAuthorityTypesInContractOrder() {
    List<String> bodies = new ArrayList<>();
    DataAccessApiClient client =
        new DataAccessApiClient() {
          @Override
          public void createApplication(String requestBody) {}

          @Override
          public void recordManualOutcome(UUID applicationId) {}

          @Override
          public void makeDecision(UUID applicationId, String requestBody) {}

          @Override
          public void createPriorAuthority(UUID applicationId, String requestBody) {
            bodies.add(requestBody);
          }
        };

    WorkflowResult result =
        new PriorAuthorityCreationWorkflow(client, new PriorAuthorityRequestFactory())
            .createAll(UUID.randomUUID());

    assertTrue(result.succeeded());
    assertEquals(3, bodies.size());
    assertTrue(bodies.get(0).contains("EXPERT"));
    assertTrue(bodies.get(1).contains("DISBURSEMENT"));
    assertTrue(bodies.get(2).contains("COUNSEL"));
  }
}

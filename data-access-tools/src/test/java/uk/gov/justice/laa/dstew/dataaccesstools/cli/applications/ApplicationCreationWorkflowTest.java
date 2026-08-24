package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.client.DataAccessApiClient;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.workflow.WorkflowResult;

class ApplicationCreationWorkflowTest {
  @Test
  void createsEachGrantedApplicationInLifecycleOrder() {
    RecordingClient client = new RecordingClient();
    var workflow =
        new ApplicationCreationWorkflow(
            client, new ApplicationRequestFactory(), new DecisionRequestFactory());

    WorkflowResult result = workflow.create(2, DecisionRequestFactory.Decision.GRANTED);

    assertTrue(result.succeeded());
    assertEquals(6, client.operations.size());
    assertEquals("create", client.operations.get(0));
    assertEquals("manual", client.operations.get(1));
    assertEquals("decision:GRANTED", client.operations.get(2));
    assertEquals("create", client.operations.get(3));
  }

  private static final class RecordingClient implements DataAccessApiClient {
    private final List<String> operations = new ArrayList<>();

    @Override
    public void createApplication(String requestBody) {
      operations.add("create");
    }

    @Override
    public void recordManualOutcome(UUID applicationId) {
      operations.add("manual");
    }

    @Override
    public void makeDecision(UUID applicationId, String requestBody) {
      operations.add(requestBody.contains("GRANTED") ? "decision:GRANTED" : "decision:REFUSED");
    }

    @Override
    public void createPriorAuthority(UUID applicationId, String requestBody) {
      throw new UnsupportedOperationException();
    }
  }
}

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
    assertEquals(8, client.operations.size());
    assertEquals("create", client.operations.get(0));
    assertEquals("manual", client.operations.get(1));
    assertEquals("assign", client.operations.get(2));
    assertEquals("decision:GRANTED", client.operations.get(3));
    assertEquals("create", client.operations.get(4));
  }

  @Test
  void includesCaseworkerIdInDecisionRequest() {
    var application = new ApplicationRequestFactory().create();

    String request =
        new DecisionRequestFactory()
            .create(application, DecisionRequestFactory.Decision.GRANTED, UUID.randomUUID());

    assertTrue(request.matches("(?s).*\\\"caseworkerId\\\":\\\"[0-9a-f-]{36}\\\".*"));
  }

  @Test
  void createsManualApplicationsWithoutMakingADecision() {
    RecordingClient client = new RecordingClient();
    var workflow =
        new ApplicationCreationWorkflow(
            client, new ApplicationRequestFactory(), new DecisionRequestFactory());

    WorkflowResult result = workflow.createManual(2);

    assertTrue(result.succeeded());
    assertEquals(List.of("create", "manual", "create", "manual"), client.operations);
  }

  @Test
  void createsEachAutograntedApplicationWithoutManualOutcomeOrDecision() {
    RecordingClient client = new RecordingClient();
    var workflow =
        new ApplicationCreationWorkflow(
            client, new ApplicationRequestFactory(), new DecisionRequestFactory());

    WorkflowResult result = workflow.createAutogranted(2);

    assertTrue(result.succeeded());
    assertEquals(List.of("create", "autogranted", "create", "autogranted"), client.operations);
  }

  private static final class RecordingClient implements DataAccessApiClient {
    private final List<String> operations = new ArrayList<>();

    @Override
    public UUID createApplication(String requestBody) {
      operations.add("create");
      return UUID.randomUUID();
    }

    @Override
    public void recordManualOutcome(UUID applicationId) {
      operations.add("manual");
    }

    @Override
    public void recordAutograntedOutcome(UUID applicationId, String requestBody) {
      assertTrue(requestBody.contains("\"outcome\":\"AUTOGRANTED\""));
      assertTrue(requestBody.contains("\"certificate\""));
      operations.add("autogranted");
    }

    @Override
    public void assignWorkListItem(
        UUID itemId, UUID caseworkerId, long expectedAssignmentVersion, String eventDescription) {
      operations.add("assign");
    }

    @Override
    public void makeDecision(UUID applicationId, String requestBody) {
      operations.add(requestBody.contains("GRANTED") ? "decision:GRANTED" : "decision:REFUSED");
    }

    @Override
    public UUID createPriorAuthorityDraft(String requestBody) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void updatePriorAuthorityDraft(UUID priorAuthorityId, String requestBody) {
      throw new UnsupportedOperationException();
    }

    @Override
    public UUID submitPriorAuthorityDraft(UUID priorAuthorityId) {
      throw new UnsupportedOperationException();
    }
  }
}

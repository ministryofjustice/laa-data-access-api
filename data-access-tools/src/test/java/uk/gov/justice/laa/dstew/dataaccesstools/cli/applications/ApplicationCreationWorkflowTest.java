package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.client.DataAccessApiClient;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.workflow.WorkflowResult;

class ApplicationCreationWorkflowTest {
  @ParameterizedTest
  @EnumSource(ApplicationCreationWorkflow.Outcome.class)
  void createsEachApplicationInLifecycleOrder(ApplicationCreationWorkflow.Outcome outcome) {
    RecordingClient client = new RecordingClient();
    var workflow =
        new ApplicationCreationWorkflow(
            client, new ApplicationRequestFactory(), new DecisionRequestFactory());

    WorkflowResult result = workflow.create(2, outcome);

    assertTrue(result.succeeded());
    List<String> expected =
        switch (outcome) {
          case SUBMITTED -> List.of("draft", "submit");
          case MANUAL -> List.of("draft", "submit", "wait", "manual");
          case AUTOGRANTED -> List.of("draft", "submit", "wait", "autogranted");
          case GRANTED, REFUSED ->
              List.of("draft", "submit", "wait", "manual", "assign", "decision:" + outcome);
        };
    List<String> batch = new ArrayList<>(expected);
    batch.addAll(expected);
    assertEquals(batch, client.operations);
    assertEquals("APPLICATION_" + outcome, result.items().getFirst().state());
  }

  @Test
  void doesNotIncludeCaseworkerIdInDecisionRequest() {
    var application = new ApplicationRequestFactory().create();

    String request =
        new DecisionRequestFactory().create(application, DecisionRequestFactory.Decision.GRANTED);

    assertFalse(request.contains("\"caseworkerId\""));
  }

  @Test
  void createsManualApplicationsWithoutMakingADecision() {
    RecordingClient client = new RecordingClient();
    var workflow =
        new ApplicationCreationWorkflow(
            client, new ApplicationRequestFactory(), new DecisionRequestFactory());

    WorkflowResult result = workflow.create(2, ApplicationCreationWorkflow.Outcome.MANUAL);

    assertTrue(result.succeeded());
    assertEquals(
        List.of("draft", "submit", "wait", "manual", "draft", "submit", "wait", "manual"),
        client.operations);
  }

  @Test
  void createsEachAutograntedApplicationWithoutManualOutcomeOrDecision() {
    RecordingClient client = new RecordingClient();
    var workflow =
        new ApplicationCreationWorkflow(
            client, new ApplicationRequestFactory(), new DecisionRequestFactory());

    WorkflowResult result = workflow.create(2, ApplicationCreationWorkflow.Outcome.AUTOGRANTED);

    assertTrue(result.succeeded());
    assertEquals(
        List.of("draft", "submit", "wait", "autogranted", "draft", "submit", "wait", "autogranted"),
        client.operations);
  }

  @Test
  void createsCompleteDraftsWithoutSubmittingOrApplyingOutcomes() {
    RecordingClient client = new RecordingClient();
    WorkflowResult result = workflow(client).createDrafts(2);
    assertTrue(result.succeeded());
    assertEquals(List.of("draft", "draft"), client.operations);
    assertTrue(result.items().stream().allMatch(item -> item.state().equals("APPLICATION_DRAFT")));
  }

  @Test
  void submitsExistingDraftWithoutRecreatingItsContent() {
    RecordingClient client = new RecordingClient();
    UUID applicationId = UUID.randomUUID();
    WorkflowResult result = workflow(client).submitDraft(applicationId);
    assertTrue(result.succeeded());
    assertEquals(List.of("submit"), client.operations);
    assertEquals(applicationId, result.items().getFirst().applicationId());
    assertEquals("APPLICATION_SUBMITTED", result.items().getFirst().state());
  }

  @ParameterizedTest
  @ValueSource(strings = {"draft", "submit", "wait", "manual", "assign", "decision:GRANTED"})
  void stopsFailedItemAndContinuesBatch(String failedOperation) {
    RecordingClient client = new RecordingClient();
    client.failedOperation = failedOperation;
    WorkflowResult result = workflow(client).create(2, ApplicationCreationWorkflow.Outcome.GRANTED);
    assertFalse(result.succeeded());
    assertFalse(result.items().getFirst().succeeded());
    assertTrue(result.items().getLast().succeeded());
    List<String> complete =
        List.of("draft", "submit", "wait", "manual", "assign", "decision:GRANTED");
    List<String> expected =
        new ArrayList<>(complete.subList(0, complete.indexOf(failedOperation) + 1));
    expected.addAll(complete);
    assertEquals(expected, client.operations);
    assertTrue(result.items().getFirst().detail().contains("Failed at"));
    assertTrue(result.items().getFirst().applicationId() != null);
    String expectedState =
        switch (failedOperation) {
          case "draft" -> "APPLICATION_NOT_CREATED";
          case "submit" -> "APPLICATION_DRAFT";
          case "wait", "manual" -> "APPLICATION_SUBMITTED";
          case "assign" -> "APPLICATION_MANUAL";
          default -> "APPLICATION_ASSIGNED";
        };
    assertEquals(expectedState, result.items().getFirst().state());
  }

  @Test
  void reportsExistingDraftSubmissionFailure() {
    RecordingClient client = new RecordingClient();
    client.failedOperation = "submit";
    UUID applicationId = UUID.randomUUID();
    WorkflowResult result = workflow(client).submitDraft(applicationId);
    assertFalse(result.succeeded());
    assertEquals(applicationId, result.items().getFirst().applicationId());
    assertEquals("APPLICATION_DRAFT", result.items().getFirst().state());
  }

  private ApplicationCreationWorkflow workflow(RecordingClient client) {
    return new ApplicationCreationWorkflow(
        client, new ApplicationRequestFactory(42), new DecisionRequestFactory());
  }

  private static final class RecordingClient implements DataAccessApiClient {
    private final List<String> operations = new ArrayList<>();
    private String failedOperation;

    private void record(String operation) {
      operations.add(operation);
      if (operation.equals(failedOperation)) {
        failedOperation = null;
        throw new IllegalStateException("simulated failure");
      }
    }

    @Override
    public UUID createApplicationDraft(String requestBody) {
      record("draft");
      try {
        var body = new ObjectMapper().readTree(requestBody);
        assertEquals("APPLICATION_SUBMITTED", body.required("status").asText());
        assertTrue(
            body.required("applicationContent")
                .required("proceedings")
                .get(0)
                .required("leadProceeding")
                .asBoolean());
        return UUID.fromString(body.required("id").asText());
      } catch (java.io.IOException exception) {
        throw new AssertionError(exception);
      }
    }

    @Override
    public UUID submitApplicationDraft(UUID applicationId) {
      record("submit");
      return applicationId;
    }

    @Override
    public void awaitApplicationReadable(UUID applicationId) {
      record("wait");
    }

    @Override
    public void recordManualOutcome(UUID applicationId) {
      record("manual");
    }

    @Override
    public void recordAutograntedOutcome(UUID applicationId, String requestBody) {
      assertTrue(requestBody.contains("\"outcome\":\"AUTOGRANTED\""));
      assertTrue(requestBody.contains("\"certificate\""));
      record("autogranted");
    }

    @Override
    public void assignWorkListItem(
        UUID itemId, long expectedAssignmentVersion, String eventDescription) {
      assertEquals(0, expectedAssignmentVersion);
      record("assign");
    }

    @Override
    public void makeDecision(UUID applicationId, String requestBody) {
      assertFalse(requestBody.contains("\"caseworkerId\""));
      record(requestBody.contains("GRANTED") ? "decision:GRANTED" : "decision:REFUSED");
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

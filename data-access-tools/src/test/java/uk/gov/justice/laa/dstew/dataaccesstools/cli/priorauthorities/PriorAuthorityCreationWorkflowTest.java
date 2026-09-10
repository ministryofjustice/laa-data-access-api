package uk.gov.justice.laa.dstew.dataaccesstools.cli.priorauthorities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.ApplicationRequestFactory;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.DecisionRequestFactory;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.client.DataAccessApiClient;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.workflow.WorkflowResult;

class PriorAuthorityCreationWorkflowTest {
  @Test
  void createsDraftsForAllTypesInContractOrder() {
    List<String> bodies = new ArrayList<>();
    DataAccessApiClient client =
        new DataAccessApiClient() {
          @Override
          public UUID createApplication(String requestBody) {
            return UUID.randomUUID();
          }

          @Override
          public void recordManualOutcome(UUID applicationId) {}

          @Override
          public void makeDecision(UUID applicationId, String requestBody) {}

          @Override
          public UUID createPriorAuthorityDraft(String requestBody) {
            bodies.add(requestBody);
            return UUID.randomUUID();
          }

          @Override
          public void updatePriorAuthorityDraft(UUID priorAuthorityId, String requestBody) {}

          @Override
          public UUID submitPriorAuthorityDraft(UUID priorAuthorityId) {
            return priorAuthorityId;
          }
        };

    WorkflowResult result =
        new PriorAuthorityCreationWorkflow(
                client,
                new PriorAuthorityRequestFactory(),
                new ApplicationRequestFactory(),
                new DecisionRequestFactory())
            .createDrafts(UUID.randomUUID(), 1, PriorAuthorityTypeSelector.ALL);

    assertTrue(result.succeeded());
    assertEquals(3, bodies.size());
    assertTrue(bodies.get(0).contains("EXPERT"));
    assertTrue(bodies.get(1).contains("DISBURSEMENT"));
    assertTrue(bodies.get(2).contains("COUNSEL"));
    assertTrue(result.items().stream().allMatch(item -> item.applicationId() != null));
    assertTrue(result.items().stream().allMatch(item -> item.priorAuthorityId() != null));
  }

  @Test
  void createsAndSubmitsEachSelectedPriorAuthority() {
    List<String> operations = new ArrayList<>();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    DataAccessApiClient client =
        new DataAccessApiClient() {
          @Override
          public UUID createApplication(String requestBody) {
            operations.add("application");
            return UUID.randomUUID();
          }

          @Override
          public void recordManualOutcome(UUID applicationId) {
            operations.add("manual-outcome");
          }

          @Override
          public void assignWorkListItem(
              UUID itemId,
              UUID assignedCaseworkerId,
              long expectedAssignmentVersion,
              String eventDescription) {
            assertEquals(caseworkerId, assignedCaseworkerId);
            assertEquals(0, expectedAssignmentVersion);
            operations.add("assign");
          }

          @Override
          public void makeDecision(UUID applicationId, String requestBody) {
            assertTrue(requestBody.contains("\"caseworkerId\":\"" + caseworkerId + "\""));
            operations.add("granted-decision");
          }

          @Override
          public UUID createPriorAuthorityDraft(String requestBody) {
            operations.add("draft");
            return priorAuthorityId;
          }

          @Override
          public void updatePriorAuthorityDraft(UUID id, String requestBody) {
            assertEquals(priorAuthorityId, id);
            assertTrue(requestBody.contains("expertDetails"));
            operations.add("update");
          }

          @Override
          public UUID submitPriorAuthorityDraft(UUID id) {
            assertEquals(priorAuthorityId, id);
            operations.add("submit");
            return priorAuthorityId;
          }
        };

    WorkflowResult result =
        new PriorAuthorityCreationWorkflow(
                client,
                new PriorAuthorityRequestFactory(),
                new ApplicationRequestFactory(),
                new DecisionRequestFactory())
            .createSubmitted(1, PriorAuthorityTypeSelector.EXPERT, caseworkerId);

    assertTrue(result.succeeded());
    assertEquals(
        List.of(
            "application",
            "manual-outcome",
            "assign",
            "granted-decision",
            "draft",
            "update",
            "submit"),
        operations);
    assertEquals(priorAuthorityId, result.items().getFirst().priorAuthorityId());
    assertEquals("SUBMITTED", result.items().getFirst().state());
  }
}

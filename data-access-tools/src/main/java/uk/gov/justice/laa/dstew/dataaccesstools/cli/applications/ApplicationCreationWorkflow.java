package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.client.DataAccessApiClient;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.workflow.WorkflowResult;

public final class ApplicationCreationWorkflow {

  public enum Outcome {
    SUBMITTED,
    MANUAL,
    AUTOGRANTED,
    GRANTED,
    REFUSED
  }

  private final DataAccessApiClient client;
  private final ApplicationRequestFactory applicationFactory;
  private final DecisionRequestFactory decisionFactory;

  public ApplicationCreationWorkflow(
      DataAccessApiClient client,
      ApplicationRequestFactory applicationFactory,
      DecisionRequestFactory decisionFactory) {
    this.client = client;
    this.applicationFactory = applicationFactory;
    this.decisionFactory = decisionFactory;
  }

  public WorkflowResult create(int count, Outcome outcome) {
    return create(count, outcome, true);
  }

  public WorkflowResult createDrafts(int count) {
    return create(count, Outcome.SUBMITTED, false);
  }

  public WorkflowResult submitDraft(UUID applicationId) {
    try {
      client.submitApplicationDraft(applicationId);
      return new WorkflowResult(
          List.of(
              result(
                  applicationId, true, "submitted; read model may lag", "APPLICATION_SUBMITTED")));
    } catch (RuntimeException exception) {
      return new WorkflowResult(
          List.of(
              result(
                  applicationId,
                  false,
                  "Failed at submit-draft: " + exception.getMessage(),
                  "APPLICATION_DRAFT")));
    }
  }

  private WorkflowResult create(int count, Outcome outcome, boolean submit) {
    List<WorkflowResult.ItemResult> results = new ArrayList<>();
    for (int item = 0; item < count; item++) {
      ApplicationRequestFactory.ApplicationData application = applicationFactory.create();
      String state = "APPLICATION_NOT_CREATED";
      String stage = "create-draft";
      try {
        UUID draftId = client.createApplicationDraft(application.request());
        if (!application.applicationId().equals(draftId)) {
          throw new IllegalStateException(
              "Draft response returned a different application ID: " + draftId);
        }
        state = "APPLICATION_DRAFT";
        if (submit) {
          stage = "submit-draft";
          client.submitApplicationDraft(application.applicationId());
          state = "APPLICATION_SUBMITTED";
          if (outcome != Outcome.SUBMITTED) {
            stage = "wait-for-projection";
            client.awaitApplicationReadable(application.applicationId());
            stage = "record-outcome";
            if (outcome == Outcome.AUTOGRANTED) {
              client.recordAutograntedOutcome(
                  application.applicationId(),
                  decisionFactory.createAutograntedOutcome(application));
              state = "APPLICATION_AUTOGRANTED";
            } else {
              client.recordManualOutcome(application.applicationId());
              state = "APPLICATION_MANUAL";
              if (outcome == Outcome.GRANTED || outcome == Outcome.REFUSED) {
                stage = "assign";
                client.assignWorkListItem(
                    application.applicationId(),
                    0,
                    "Application assigned by data-access-tools for decision");
                state = "APPLICATION_ASSIGNED";
                stage = "make-decision";
                client.makeDecision(
                    application.applicationId(),
                    decisionFactory.create(
                        application, DecisionRequestFactory.Decision.valueOf(outcome.name())));
                state = "APPLICATION_" + outcome;
              }
            }
          }
        }
        results.add(
            result(
                application.applicationId(),
                true,
                application.laaReference()
                    + (submit && outcome == Outcome.SUBMITTED
                        ? " submitted; read model may lag"
                        : ""),
                state));
      } catch (RuntimeException exception) {
        results.add(
            result(
                application.applicationId(),
                false,
                "Failed at " + stage + ": " + exception.getMessage(),
                state));
      }
    }
    return new WorkflowResult(results);
  }

  private WorkflowResult.ItemResult result(
      UUID applicationId, boolean succeeded, String detail, String state) {
    return new WorkflowResult.ItemResult(
        applicationId.toString(), succeeded, detail, applicationId, null, state);
  }
}

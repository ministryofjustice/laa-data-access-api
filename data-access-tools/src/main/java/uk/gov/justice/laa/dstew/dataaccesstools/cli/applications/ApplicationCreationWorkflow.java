package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import java.util.ArrayList;
import java.util.List;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.client.DataAccessApiClient;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.workflow.WorkflowResult;

public final class ApplicationCreationWorkflow {
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

  public WorkflowResult create(int count, DecisionRequestFactory.Decision decision) {
    List<WorkflowResult.ItemResult> results = new ArrayList<>();
    for (int item = 0; item < count; item++) {
      ApplicationRequestFactory.ApplicationData application = applicationFactory.create();
      try {
        client.createApplication(application.request());
        client.recordManualOutcome(application.applicationId());
        client.makeDecision(
            application.applicationId(), decisionFactory.create(application, decision));
        results.add(
            new WorkflowResult.ItemResult(
                application.applicationId().toString(),
                true,
                decision + " " + application.laaReference(),
                null));
      } catch (RuntimeException exception) {
        results.add(
            new WorkflowResult.ItemResult(
                application.applicationId().toString(), false, exception.getMessage(), null));
      }
    }
    return new WorkflowResult(results);
  }

  public WorkflowResult createManual(int count) {
    List<WorkflowResult.ItemResult> results = new ArrayList<>();
    for (int item = 0; item < count; item++) {
      ApplicationRequestFactory.ApplicationData application = applicationFactory.create();
      try {
        client.createApplication(application.request());
        client.recordManualOutcome(application.applicationId());
        results.add(
            new WorkflowResult.ItemResult(
                application.applicationId().toString(),
                true,
                "MANUAL " + application.laaReference()));
      } catch (RuntimeException exception) {
        results.add(
            new WorkflowResult.ItemResult(
                application.applicationId().toString(), false, exception.getMessage()));
      }
    }
    return new WorkflowResult(results);
  }

  public WorkflowResult createAutogranted(int count) {
    List<WorkflowResult.ItemResult> results = new ArrayList<>();
    for (int item = 0; item < count; item++) {
      ApplicationRequestFactory.ApplicationData application = applicationFactory.create();
      try {
        client.createApplication(application.request());
        client.recordAutograntedOutcome(
            application.applicationId(), decisionFactory.createAutograntedOutcome(application));
        results.add(
            new WorkflowResult.ItemResult(
                application.applicationId().toString(),
                true,
                "AUTOGRANTED " + application.laaReference()));
      } catch (RuntimeException exception) {
        results.add(
            new WorkflowResult.ItemResult(
                application.applicationId().toString(), false, exception.getMessage()));
      }
    }
    return new WorkflowResult(results);
  }
}

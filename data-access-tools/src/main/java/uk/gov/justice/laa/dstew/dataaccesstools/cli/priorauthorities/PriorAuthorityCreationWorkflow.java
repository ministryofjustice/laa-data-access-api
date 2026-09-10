package uk.gov.justice.laa.dstew.dataaccesstools.cli.priorauthorities;

import java.util.ArrayList;
import java.util.UUID;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.ApplicationRequestFactory;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.applications.DecisionRequestFactory;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.priorauthorities.PriorAuthorityRequestFactory.PriorAuthorityType;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.client.DataAccessApiClient;
import uk.gov.justice.laa.dstew.dataaccesstools.utils.workflow.WorkflowResult;

public final class PriorAuthorityCreationWorkflow {
  private final DataAccessApiClient client;
  private final PriorAuthorityRequestFactory requestFactory;
  private final ApplicationRequestFactory applicationFactory;
  private final DecisionRequestFactory decisionFactory;

  public PriorAuthorityCreationWorkflow(
      DataAccessApiClient client,
      PriorAuthorityRequestFactory requestFactory,
      ApplicationRequestFactory applicationFactory,
      DecisionRequestFactory decisionFactory) {
    this.client = client;
    this.requestFactory = requestFactory;
    this.applicationFactory = applicationFactory;
    this.decisionFactory = decisionFactory;
  }

  public WorkflowResult createDrafts(
      UUID applicationId, int count, PriorAuthorityTypeSelector typeSelector) {
    var results = new ArrayList<WorkflowResult.ItemResult>();
    for (PriorAuthorityType type : requestFactory.types(typeSelector)) {
      for (int item = 0; item < count; item++) {
        results.add(createDraft(applicationId, type));
      }
    }
    return new WorkflowResult(results);
  }

  public WorkflowResult createSubmitted(
      int count, PriorAuthorityTypeSelector typeSelector, UUID caseworkerId) {
    var results = new ArrayList<WorkflowResult.ItemResult>();
    for (PriorAuthorityType type : requestFactory.types(typeSelector)) {
      for (int item = 0; item < count; item++) {
        results.add(createSubmitted(type, caseworkerId));
      }
    }
    return new WorkflowResult(results);
  }

  private WorkflowResult.ItemResult createDraft(UUID applicationId, PriorAuthorityType type) {
    try {
      UUID priorAuthorityId =
          client.createPriorAuthorityDraft(requestFactory.createDraft(applicationId, type));
      return result(type, true, "created", applicationId, priorAuthorityId, "DRAFT");
    } catch (RuntimeException exception) {
      return result(type, false, exception.getMessage(), applicationId, null, "DRAFT");
    }
  }

  private WorkflowResult.ItemResult createSubmitted(PriorAuthorityType type, UUID caseworkerId) {
    var application = applicationFactory.create();
    UUID priorAuthorityId = null;
    try {
      client.createApplication(application.request());
      client.recordManualOutcome(application.applicationId());
      client.assignWorkListItem(
          application.applicationId(),
          caseworkerId,
          0,
          "Application assigned by data-access-tools for prior-authority submission");
      client.makeDecision(
          application.applicationId(),
          decisionFactory.create(
              application, DecisionRequestFactory.Decision.GRANTED, caseworkerId));
      priorAuthorityId =
          client.createPriorAuthorityDraft(
              requestFactory.createDraft(application.applicationId(), type));
      client.updatePriorAuthorityDraft(priorAuthorityId, requestFactory.saveDraft(type));
      UUID submittedPriorAuthorityId = client.submitPriorAuthorityDraft(priorAuthorityId);
      return result(
          type,
          true,
          "created and submitted",
          application.applicationId(),
          submittedPriorAuthorityId,
          "SUBMITTED");
    } catch (RuntimeException exception) {
      return result(
          type,
          false,
          exception.getMessage(),
          application.applicationId(),
          priorAuthorityId,
          "SUBMITTED");
    }
  }

  private WorkflowResult.ItemResult result(
      PriorAuthorityType type,
      boolean succeeded,
      String detail,
      UUID applicationId,
      UUID priorAuthorityId,
      String state) {
    return new WorkflowResult.ItemResult(
        type.name(), succeeded, detail, applicationId, priorAuthorityId, state);
  }
}

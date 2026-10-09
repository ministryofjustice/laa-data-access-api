package uk.gov.justice.laa.dstew.access.command.application.handler;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftStore;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

final class ApplicationCommandHandlerSupport {

  private ApplicationCommandHandlerSupport() {}

  static void requireApplicationExists(
      ApplicationAggregate application, UUID requestedApplicationId) {
    if (application.getApplicationId() == null) {
      throw new ResourceNotFoundException(
          "No application found with Application ID: " + requestedApplicationId);
    }
  }

  static void requireApplicationDraft(
      ApplicationAggregate application, UUID requestedApplicationId) {
    requireApplicationExists(application, requestedApplicationId);
    if (application.getState().getStatus() != null) {
      throw new ValidationException(
          List.of("Documents can only be uploaded to an application draft"));
    }
  }

  static ApplicationDraftPayload requireDraft(
      UUID requestedApplicationId, ApplicationDraftStore draftStore) {
    return draftStore
        .find(requestedApplicationId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "No application draft found with Application ID: " + requestedApplicationId));
  }

  static void validateDirectWorkItem(
      ApplicationAggregate application, UUID workItemId, long expectedAssignmentVersion) {
    requireApplicationExists(application, workItemId);
    if (!application.getApplicationId().equals(workItemId)) {
      throw new ResourceNotFoundException("No application work item found with id: " + workItemId);
    }
    if (application.getState().getAutoGranted() != AutoGrantedState.MANUAL
        || application.getState().getOverallDecision() != null) {
      throw new ResourceNotFoundException("Application work item is not active: " + workItemId);
    }
    if (expectedAssignmentVersion != application.getState().getAssignmentVersion()) {
      throw new WorkItemAssignmentConflictException(workItemId, "the assignment version is stale");
    }
  }

  @SuppressWarnings("unchecked")
  static String officeCode(Map<String, Object> applicationContent) {
    Object provider = applicationContent.get("provider");
    if (!(provider instanceof Map<?, ?> providerMap)) {
      return null;
    }
    Object officeCode = ((Map<String, Object>) providerMap).get("officeCode");
    return officeCode instanceof String code ? code : null;
  }
}

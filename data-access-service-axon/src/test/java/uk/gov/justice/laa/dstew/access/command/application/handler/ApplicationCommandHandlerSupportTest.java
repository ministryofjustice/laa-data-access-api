package uk.gov.justice.laa.dstew.access.command.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftStore;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

class ApplicationCommandHandlerSupportTest {

  @Test
  void givenApplicationWithValidId_whenRequireApplicationExists_thenDoesNotThrow() {
    UUID applicationId = UUID.randomUUID();
    ApplicationAggregate application = mockApplicationWithId(applicationId);

    // Should not throw
    ApplicationCommandHandlerSupport.requireApplicationExists(application, applicationId);
  }

  @Test
  void
      givenApplicationWithNullId_whenRequireApplicationExists_thenThrowsResourceNotFoundException() {
    UUID requestedId = UUID.randomUUID();
    ApplicationAggregate application = mockApplicationWithId(null);

    assertThatThrownBy(
            () ->
                ApplicationCommandHandlerSupport.requireApplicationExists(application, requestedId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("No application found with Application ID: " + requestedId);
  }

  @Test
  void givenApplicationDraftWithNullStatus_whenRequireApplicationDraft_thenDoesNotThrow() {
    UUID applicationId = UUID.randomUUID();
    ApplicationAggregate application = mockApplicationWithIdAndStatus(applicationId, null);

    // Should not throw
    ApplicationCommandHandlerSupport.requireApplicationDraft(application, applicationId);
  }

  @Test
  void
      givenApplicationWithNonNullStatus_whenRequireApplicationDraft_thenThrowsValidationException() {
    UUID applicationId = UUID.randomUUID();
    ApplicationAggregate application =
        mockApplicationWithIdAndStatus(applicationId, "APPLICATION_SUBMITTED");

    assertThatThrownBy(
            () ->
                ApplicationCommandHandlerSupport.requireApplicationDraft(
                    application, applicationId))
        .isInstanceOf(ValidationException.class)
        .hasMessage("One or more validation rules were violated");
  }

  @Test
  void
      givenApplicationWithNullId_whenRequireApplicationDraft_thenThrowsResourceNotFoundException() {
    UUID requestedId = UUID.randomUUID();
    ApplicationAggregate application = mockApplicationWithId(null);

    assertThatThrownBy(
            () ->
                ApplicationCommandHandlerSupport.requireApplicationDraft(application, requestedId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("No application found with Application ID: " + requestedId);
  }

  @Test
  void givenDraftStore_whenRequireDraftAndDraftExists_thenReturnsDraft() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDraftPayload expectedDraft = mock(ApplicationDraftPayload.class);
    ApplicationDraftStore draftStore = mock(ApplicationDraftStore.class);
    when(draftStore.find(applicationId)).thenReturn(Optional.of(expectedDraft));

    ApplicationDraftPayload result =
        ApplicationCommandHandlerSupport.requireDraft(applicationId, draftStore);

    assertThat(result).isEqualTo(expectedDraft);
  }

  @Test
  void givenDraftStore_whenRequireDraftAndDraftDoesNotExist_thenThrowsResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDraftStore draftStore = mock(ApplicationDraftStore.class);
    when(draftStore.find(applicationId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> ApplicationCommandHandlerSupport.requireDraft(applicationId, draftStore))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("No application draft found with Application ID: " + applicationId);
  }

  @Test
  void givenActiveManualWorkItem_whenValidateDirectWorkItem_thenDoesNotThrow() {
    UUID workItemId = UUID.randomUUID();
    ApplicationAggregate application =
        mockDirectWorkItem(workItemId, AutoGrantedState.MANUAL, null, 1L);

    // Should not throw
    ApplicationCommandHandlerSupport.validateDirectWorkItem(application, workItemId, 1L);
  }

  @Test
  void givenApplicationWithNullId_whenValidateDirectWorkItem_thenThrowsResourceNotFoundException() {
    UUID workItemId = UUID.randomUUID();
    ApplicationAggregate application = mockApplicationWithId(null);

    assertThatThrownBy(
            () ->
                ApplicationCommandHandlerSupport.validateDirectWorkItem(
                    application, workItemId, 1L))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void givenMismatchedWorkItemIds_whenValidateDirectWorkItem_thenThrowsResourceNotFoundException() {
    UUID workItemId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ApplicationAggregate application =
        mockDirectWorkItem(applicationId, AutoGrantedState.MANUAL, null, 1L);

    assertThatThrownBy(
            () ->
                ApplicationCommandHandlerSupport.validateDirectWorkItem(
                    application, workItemId, 1L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("No application work item found with id: " + workItemId);
  }

  @Test
  void givenAutoGrantedWorkItem_whenValidateDirectWorkItem_thenThrowsResourceNotFoundException() {
    UUID workItemId = UUID.randomUUID();
    ApplicationAggregate application =
        mockDirectWorkItem(workItemId, AutoGrantedState.AUTOGRANTED, null, 1L);

    assertThatThrownBy(
            () ->
                ApplicationCommandHandlerSupport.validateDirectWorkItem(
                    application, workItemId, 1L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Application work item is not active: " + workItemId);
  }

  @Test
  void givenWorkItemWithDecision_whenValidateDirectWorkItem_thenThrowsResourceNotFoundException() {
    UUID workItemId = UUID.randomUUID();
    ApplicationAggregate application =
        mockDirectWorkItem(workItemId, AutoGrantedState.MANUAL, "GRANTED", 1L);

    assertThatThrownBy(
            () ->
                ApplicationCommandHandlerSupport.validateDirectWorkItem(
                    application, workItemId, 1L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Application work item is not active: " + workItemId);
  }

  @Test
  void givenStaleAssignmentVersion_whenValidateDirectWorkItem_thenThrowsConflictException() {
    UUID workItemId = UUID.randomUUID();
    ApplicationAggregate application =
        mockDirectWorkItem(workItemId, AutoGrantedState.MANUAL, null, 2L);

    assertThatThrownBy(
            () ->
                ApplicationCommandHandlerSupport.validateDirectWorkItem(
                    application, workItemId, 1L))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessageContaining("the assignment version is stale");
  }

  @Test
  void givenMapWithValidProvider_whenOfficeCode_thenReturnsOfficeCode() {
    String expectedCode = "S123";
    Map<String, Object> content = new HashMap<>();
    Map<String, Object> provider = new HashMap<>();
    provider.put("officeCode", expectedCode);
    content.put("provider", provider);

    String result = ApplicationCommandHandlerSupport.officeCode(content);

    assertThat(result).isEqualTo(expectedCode);
  }

  @Test
  void givenMapWithNonStringOfficeCode_whenOfficeCode_thenReturnsNull() {
    Map<String, Object> content = new HashMap<>();
    Map<String, Object> provider = new HashMap<>();
    provider.put("officeCode", 12345);
    content.put("provider", provider);

    String result = ApplicationCommandHandlerSupport.officeCode(content);

    assertThat(result).isNull();
  }

  @Test
  void givenMapWithoutOfficeCode_whenOfficeCode_thenReturnsNull() {
    Map<String, Object> content = new HashMap<>();
    Map<String, Object> provider = new HashMap<>();
    content.put("provider", provider);

    String result = ApplicationCommandHandlerSupport.officeCode(content);

    assertThat(result).isNull();
  }

  @Test
  void givenMapWithNonMapProvider_whenOfficeCode_thenReturnsNull() {
    Map<String, Object> content = new HashMap<>();
    content.put("provider", "notAMap");

    String result = ApplicationCommandHandlerSupport.officeCode(content);

    assertThat(result).isNull();
  }

  @Test
  void givenMapWithoutProvider_whenOfficeCode_thenReturnsNull() {
    Map<String, Object> content = new HashMap<>();

    String result = ApplicationCommandHandlerSupport.officeCode(content);

    assertThat(result).isNull();
  }

  @Test
  void givenMapWithNullProvider_whenOfficeCode_thenReturnsNull() {
    Map<String, Object> content = new HashMap<>();
    content.put("provider", null);

    String result = ApplicationCommandHandlerSupport.officeCode(content);

    assertThat(result).isNull();
  }

  // Helper methods

  private ApplicationAggregate mockApplicationWithId(UUID applicationId) {
    ApplicationAggregate application = mock(ApplicationAggregate.class);
    when(application.getApplicationId()).thenReturn(applicationId);
    return application;
  }

  private ApplicationAggregate mockApplicationWithIdAndStatus(UUID applicationId, String status) {
    ApplicationAggregate application = mock(ApplicationAggregate.class);
    ApplicationState state = mock(ApplicationState.class);
    when(application.getApplicationId()).thenReturn(applicationId);
    when(application.getState()).thenReturn(state);
    when(state.getStatus()).thenReturn(status);
    return application;
  }

  private ApplicationAggregate mockDirectWorkItem(
      UUID workItemId, AutoGrantedState autoGranted, String decision, long assignmentVersion) {
    ApplicationAggregate application = mock(ApplicationAggregate.class);
    ApplicationState state = mock(ApplicationState.class);
    when(application.getApplicationId()).thenReturn(workItemId);
    when(application.getState()).thenReturn(state);
    when(state.getAutoGranted()).thenReturn(autoGranted);
    when(state.getOverallDecision()).thenReturn(decision);
    when(state.getAssignmentVersion()).thenReturn(assignmentVersion);
    return application;
  }
}

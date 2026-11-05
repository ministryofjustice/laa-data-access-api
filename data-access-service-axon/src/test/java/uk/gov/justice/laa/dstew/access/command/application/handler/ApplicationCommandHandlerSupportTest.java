package uk.gov.justice.laa.dstew.access.command.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationEvolve;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftContent;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;
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
    ApplicationCommandHandlerSupport.requireApplicationDraft(application);
  }

  @Test
  void
      givenApplicationWithNonNullStatus_whenRequireApplicationDraft_thenThrowsValidationException() {
    UUID applicationId = UUID.randomUUID();
    ApplicationAggregate application =
        mockApplicationWithIdAndStatus(applicationId, "APPLICATION_SUBMITTED");

    assertThatThrownBy(() -> ApplicationCommandHandlerSupport.requireApplicationDraft(application))
        .isInstanceOf(ValidationException.class)
        .hasMessage("One or more validation rules were violated");
  }

  @Test
  void givenOpenDraft_whenRequireDraft_thenReturnsContentAndFilenamesFromEvents() {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    ApplicationState state = new ApplicationState();
    ApplicationEvolve.apply(state, draftStarted(applicationId, Erasable.of(Map.of("a", "b"))));
    ApplicationEvolve.apply(state, uploaded(applicationId, documentId, "evidence.pdf"));

    ApplicationDraftContent draft =
        ApplicationCommandHandlerSupport.requireDraft(
            mockApplication(applicationId, state), applicationId);

    assertThat(draft)
        .isEqualTo(
            new ApplicationDraftContent(
                "APPLICATION_SUBMITTED",
                "LAA-123",
                Map.of("a", "b"),
                "{}",
                List.of(),
                Map.of(documentId, "evidence.pdf")));
  }

  @Test
  void givenErasedDraftContent_whenRequireDraft_thenThrowsResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();
    ApplicationState state = new ApplicationState();
    ApplicationEvolve.apply(state, draftStarted(applicationId, Erasable.erased()));

    assertThatThrownBy(
            () ->
                ApplicationCommandHandlerSupport.requireDraft(
                    mockApplication(applicationId, state), applicationId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("No application draft found with Application ID: " + applicationId);
  }

  @Test
  void givenSubmittedApplication_whenFindDraft_thenEmpty() {
    UUID applicationId = UUID.randomUUID();
    ApplicationState state = new ApplicationState();
    ApplicationEvolve.apply(state, draftStarted(applicationId, Erasable.of(Map.of())));
    ApplicationEvolve.apply(state, uploaded(applicationId, UUID.randomUUID(), "evidence.pdf"));
    ApplicationEvolve.apply(state, applicationCreatedEvent(applicationId));

    assertThat(ApplicationCommandHandlerSupport.findDraft(mockApplication(applicationId, state)))
        .isEmpty();
    assertThat(state.getDraftDocumentFilenames()).isEmpty();
  }

  @Test
  void givenNoApplication_whenFindDraft_thenEmpty() {
    assertThat(ApplicationCommandHandlerSupport.findDraft(null)).isEmpty();
    assertThat(ApplicationCommandHandlerSupport.findDraft(mockApplicationWithId(null))).isEmpty();
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

  private ApplicationAggregate mockApplication(UUID applicationId, ApplicationState state) {
    ApplicationAggregate application = mock(ApplicationAggregate.class);
    when(application.getApplicationId()).thenReturn(applicationId);
    when(application.getState()).thenReturn(state);
    return application;
  }

  private static ApplicationDraftStartedEvent draftStarted(
      UUID applicationId, Erasable<Map<String, Object>> content) {
    return new ApplicationDraftStartedEvent(
        applicationId,
        1,
        "fingerprint",
        Instant.parse("2026-11-05T10:00:00Z"),
        null,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        content,
        Erasable.of("{}"),
        Erasable.of(List.of()));
  }

  private static ApplicationDocumentUploadedEvent uploaded(
      UUID applicationId, UUID documentId, String filename) {
    return new ApplicationDocumentUploadedEvent(
        applicationId,
        documentId,
        "GATEWAY_EVIDENCE",
        Instant.parse("2026-11-05T10:00:00Z"),
        12L,
        "application/pdf",
        "checksum",
        "CIVIL_APPLY",
        null,
        ".pdf",
        filename);
  }

  private ApplicationAggregate mockApplicationWithIdAndStatus(UUID applicationId, String status) {
    ApplicationAggregate application = mock(ApplicationAggregate.class);
    ApplicationState state = mock(ApplicationState.class);
    when(application.getApplicationId()).thenReturn(applicationId);
    when(application.getState()).thenReturn(state);
    when(state.getStatus()).thenReturn(status);
    return application;
  }

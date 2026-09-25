package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftUseCase;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftUseCase;
import uk.gov.justice.laa.dstew.access.command.application.draft.UpdateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.draft.UpdateApplicationDraftUseCase;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SaveApplicationDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SaveApplicationDraftResponse;
import uk.gov.justice.laa.dstew.access.model.SubmitApplicationDraftResponse;

/** Verifies that each controller endpoint delegates to the appropriate use case. */
@ExtendWith(MockitoExtension.class)
class ApplicationDraftCommandControllerTest {

  @Mock private CreateApplicationDraftUseCase createUseCase;
  @Mock private UpdateApplicationDraftUseCase updateUseCase;
  @Mock private SubmitApplicationDraftUseCase submitUseCase;
  @Mock private SaveApplicationDraftCommandMapper saveCommandMapper;
  @Mock private SubmitApplicationDraftCommandMapper submitCommandMapper;

  @InjectMocks private ApplicationDraftCommandController controller;

  @BeforeEach
  void setUp() {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void givenProjectedResult_whenSaveApplicationDraft_thenReturnsCreatedResponse() {
    UUID applicationId = UUID.randomUUID();
    CreateApplicationDraftRequest request =
        new CreateApplicationDraftRequest(applicationId).applicationContent(Map.of());
    CreateApplicationDraftCommand command =
        new CreateApplicationDraftCommand(
            applicationId, null, null, Map.of(), "{}", 1, Instant.now());
    when(saveCommandMapper.toCreateCommand(request, 1)).thenReturn(command);
    when(createUseCase.execute(command)).thenReturn(true);

    ResponseEntity<SaveApplicationDraftResponse> response =
        controller.saveApplicationDraft(null, request, 1);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getApplicationId()).isEqualTo(applicationId);
    assertThat(response.getBody().getSavedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().getPath())
        .isEqualTo("/api/v0/application-drafts/" + applicationId);
    verify(createUseCase).execute(command);
  }

  @Test
  void givenTimeoutResult_whenSaveApplicationDraft_thenReturnsAcceptedResponse() {
    UUID applicationId = UUID.randomUUID();
    CreateApplicationDraftRequest request =
        new CreateApplicationDraftRequest(applicationId).applicationContent(Map.of());
    CreateApplicationDraftCommand command =
        new CreateApplicationDraftCommand(
            applicationId, null, null, Map.of(), "{}", 1, Instant.now());
    when(saveCommandMapper.toCreateCommand(request, 1)).thenReturn(command);
    when(createUseCase.execute(command)).thenReturn(false);

    ResponseEntity<SaveApplicationDraftResponse> response =
        controller.saveApplicationDraft(null, request, 1);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    verify(createUseCase).execute(command);
  }

  @Test
  void givenRequest_whenUpdateApplicationDraft_thenDelegatesToUseCaseAndReturnsNoContent() {
    UUID applicationId = UUID.randomUUID();
    SaveApplicationDraftRequest request = new SaveApplicationDraftRequest();
    UpdateApplicationDraftCommand command =
        new UpdateApplicationDraftCommand(applicationId, null, null, Map.of(), "{}", Instant.now());
    when(saveCommandMapper.toUpdateCommand(applicationId, request)).thenReturn(command);

    ResponseEntity<Void> response = controller.updateApplicationDraft(null, applicationId, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(updateUseCase).execute(command);
  }

  @Test
  void givenProjectionConfirmed_whenSubmitApplicationDraft_thenReturnsOkResponse() {
    UUID applicationId = UUID.randomUUID();
    SubmitApplicationDraftCommand command =
        new SubmitApplicationDraftCommand(applicationId, Instant.now());
    when(submitCommandMapper.toSubmitCommand(applicationId)).thenReturn(command);
    when(submitUseCase.submit(command)).thenReturn(true);

    ResponseEntity<SubmitApplicationDraftResponse> response =
        controller.submitApplicationDraft(null, applicationId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getApplicationId()).isEqualTo(applicationId);
    assertThat(response.getBody().getSubmittedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().getPath())
        .isEqualTo("/api/v0/applications/" + applicationId);
  }

  @Test
  void givenProjectionTimeout_whenSubmitApplicationDraft_thenReturnsAcceptedResponse() {
    UUID applicationId = UUID.randomUUID();
    SubmitApplicationDraftCommand command =
        new SubmitApplicationDraftCommand(applicationId, Instant.now());
    when(submitCommandMapper.toSubmitCommand(applicationId)).thenReturn(command);
    when(submitUseCase.submit(command)).thenReturn(false);

    ResponseEntity<SubmitApplicationDraftResponse> response =
        controller.submitApplicationDraft(null, applicationId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }
}

package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityDraftUseCase;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.SubmitPriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.SubmitPriorAuthorityDraftUseCase;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.UpdatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.UpdatePriorAuthorityDraftUseCase;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SavePriorAuthorityDraftResponse;
import uk.gov.justice.laa.dstew.access.model.SubmitPriorAuthorityDraftResponse;

/** Verifies that each controller endpoint delegates to the appropriate use case. */
@ExtendWith(MockitoExtension.class)
class PriorAuthorityDraftCommandControllerTest {

  @Mock private CreatePriorAuthorityDraftUseCase createUseCase;
  @Mock private UpdatePriorAuthorityDraftUseCase updateUseCase;
  @Mock private SubmitPriorAuthorityDraftUseCase submitUseCase;
  @Mock private SavePriorAuthorityDraftCommandMapper saveCommandMapper;
  @Mock private SubmitPriorAuthorityDraftCommandMapper submitCommandMapper;

  @InjectMocks private PriorAuthorityDraftCommandController controller;

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
  void givenProjectedResult_whenSavePriorAuthorityDraft_thenReturnsCreatedResponse() {
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();
    CreatePriorAuthorityDraftRequest request =
        new CreatePriorAuthorityDraftRequest().applicationId(applicationId);
    CreatePriorAuthorityDraftCommand command =
        new CreatePriorAuthorityDraftCommand(
            priorAuthorityId, applicationId, null, "{}", 1, "PriorAuthority.json", Instant.now());
    when(saveCommandMapper.toCreateCommand(request)).thenReturn(command);
    when(createUseCase.execute(command)).thenReturn(true);

    ResponseEntity<SavePriorAuthorityDraftResponse> response =
        controller.savePriorAuthorityDraft(null, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().getPath())
        .isEqualTo("/api/v0/prior-authorities/" + priorAuthorityId);
    verify(createUseCase).execute(command);
  }

  @Test
  void givenTimeoutResult_whenSavePriorAuthorityDraft_thenReturnsAcceptedResponse() {
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();
    CreatePriorAuthorityDraftRequest request =
        new CreatePriorAuthorityDraftRequest().applicationId(applicationId);
    CreatePriorAuthorityDraftCommand command =
        new CreatePriorAuthorityDraftCommand(
            priorAuthorityId, applicationId, null, "{}", 1, "PriorAuthority.json", Instant.now());
    when(saveCommandMapper.toCreateCommand(request)).thenReturn(command);
    when(createUseCase.execute(command)).thenReturn(false);

    ResponseEntity<SavePriorAuthorityDraftResponse> response =
        controller.savePriorAuthorityDraft(null, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    verify(createUseCase).execute(command);
  }

  @Test
  void givenRequest_whenUpdatePriorAuthorityDraft_thenDelegatesToUseCaseAndReturnsNoContent() {
    UUID priorAuthorityId = UUID.randomUUID();
    SavePriorAuthorityDraftRequest request = new SavePriorAuthorityDraftRequest();
    UpdatePriorAuthorityDraftCommand command =
        new UpdatePriorAuthorityDraftCommand(
            priorAuthorityId, null, "{}", 1, "PriorAuthority.json", Instant.now());
    when(saveCommandMapper.toUpdateCommand(priorAuthorityId, request)).thenReturn(command);

    ResponseEntity<Void> response =
        controller.updatePriorAuthorityDraft(null, priorAuthorityId, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(updateUseCase).execute(command);
  }

  @Test
  void givenProjectionConfirmed_whenSubmitPriorAuthorityDraft_thenReturnsCreatedResponse() {
    UUID priorAuthorityId = UUID.randomUUID();
    SubmitPriorAuthorityDraftCommand command =
        new SubmitPriorAuthorityDraftCommand(priorAuthorityId, Instant.now());
    when(submitCommandMapper.toSubmitCommand(priorAuthorityId)).thenReturn(command);
    when(submitUseCase.submit(command)).thenReturn(true);

    ResponseEntity<SubmitPriorAuthorityDraftResponse> response =
        controller.submitPriorAuthorityDraft(null, priorAuthorityId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().getPath())
        .isEqualTo("/api/v0/prior-authorities/" + priorAuthorityId);
  }

  @Test
  void givenProjectionTimeout_whenSubmitPriorAuthorityDraft_thenReturnsAcceptedResponse() {
    UUID priorAuthorityId = UUID.randomUUID();
    SubmitPriorAuthorityDraftCommand command =
        new SubmitPriorAuthorityDraftCommand(priorAuthorityId, Instant.now());
    when(submitCommandMapper.toSubmitCommand(priorAuthorityId)).thenReturn(command);
    when(submitUseCase.submit(command)).thenReturn(false);

    ResponseEntity<SubmitPriorAuthorityDraftResponse> response =
        controller.submitPriorAuthorityDraft(null, priorAuthorityId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }
}

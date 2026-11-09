package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
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
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCallerFactory;

/** Verifies that each controller endpoint delegates to the appropriate use case. */
@ExtendWith(MockitoExtension.class)
class PriorAuthorityDraftCommandControllerTest {

  @Mock private CreatePriorAuthorityDraftUseCase createUseCase;
  @Mock private UpdatePriorAuthorityDraftUseCase updateUseCase;
  @Mock private SubmitPriorAuthorityDraftUseCase submitUseCase;
  @Mock private SavePriorAuthorityDraftCommandMapper saveCommandMapper;
  @Mock private SubmitPriorAuthorityDraftCommandMapper submitCommandMapper;
  @Mock private AuthenticatedCallerFactory authenticatedCallerFactory;

  @InjectMocks private PriorAuthorityDraftCommandController controller;

  @BeforeEach
  void setUp() {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
    lenient()
        .when(authenticatedCallerFactory.currentCaller())
        .thenReturn(new AuthenticatedCaller(Optional.empty(), Set.of(), Set.of(), Set.of()));
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void givenRequest_whenSavePriorAuthorityDraft_thenReturnsCreatedResponse() {
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();
    CreatePriorAuthorityDraftRequest request =
        new CreatePriorAuthorityDraftRequest().applicationId(applicationId);
    CreatePriorAuthorityDraftCommand command =
        new CreatePriorAuthorityDraftCommand(
            priorAuthorityId, applicationId, null, "{}", 1, "PriorAuthority.json", Instant.now());
    when(saveCommandMapper.toCreateCommand(any(), any())).thenReturn(command);

    ResponseEntity<SavePriorAuthorityDraftResponse> response =
        controller.savePriorAuthorityDraft(null, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(response.getBody().getSavedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().getPath())
        .isEqualTo("/api/v0/prior-authorities/" + priorAuthorityId);
    verify(createUseCase).execute(command);
  }

  @Test
  void givenRequest_whenUpdatePriorAuthorityDraft_thenDelegatesToUseCaseAndReturnsNoContent() {
    UUID priorAuthorityId = UUID.randomUUID();
    SavePriorAuthorityDraftRequest request = new SavePriorAuthorityDraftRequest();
    UpdatePriorAuthorityDraftCommand command =
        new UpdatePriorAuthorityDraftCommand(
            priorAuthorityId, null, "{}", 1, "PriorAuthority.json", Instant.now());
    when(saveCommandMapper.toUpdateCommand(eq(priorAuthorityId), eq(request), any()))
        .thenReturn(command);

    ResponseEntity<Void> response =
        controller.updatePriorAuthorityDraft(null, priorAuthorityId, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(updateUseCase).execute(command);
  }

  @Test
  void givenDraft_whenSubmitPriorAuthorityDraft_thenReturnsOkWithLocation() {
    UUID priorAuthorityId = UUID.randomUUID();
    Instant submittedAt = Instant.parse("2026-09-01T10:00:00Z");
    SubmitPriorAuthorityDraftCommand command =
        new SubmitPriorAuthorityDraftCommand(priorAuthorityId, submittedAt);
    when(submitCommandMapper.toSubmitCommand(eq(priorAuthorityId), any())).thenReturn(command);

    ResponseEntity<SubmitPriorAuthorityDraftResponse> response =
        controller.submitPriorAuthorityDraft(null, priorAuthorityId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(response.getBody().getSubmittedAt()).isEqualTo(submittedAt.atOffset(ZoneOffset.UTC));
    assertThat(response.getBody().getSubmittedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().getPath())
        .isEqualTo("/api/v0/prior-authorities/" + priorAuthorityId);
    verify(submitUseCase).submit(command);
  }
}

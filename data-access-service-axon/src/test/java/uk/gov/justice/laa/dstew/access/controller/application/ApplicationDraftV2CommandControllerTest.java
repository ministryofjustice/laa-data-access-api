package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftSaveResult;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftV2Command;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftV2UseCase;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftV2Command;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftV2UseCase;
import uk.gov.justice.laa.dstew.access.command.application.draft.UpdateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.draft.UpdateApplicationDraftUseCase;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDraftV2Request;
import uk.gov.justice.laa.dstew.access.model.SaveApplicationDraftV2Response;
import uk.gov.justice.laa.dstew.access.model.SubmitApplicationDraftResponse;
import uk.gov.justice.laa.dstew.access.model.UpdateApplicationDraftV2Request;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCallerFactory;

/** Verifies that the v2 draft endpoints map requests, delegate, and choose status codes. */
@ExtendWith(MockitoExtension.class)
class ApplicationDraftV2CommandControllerTest {

  @Mock private CreateApplicationDraftV2UseCase createUseCase;
  @Mock private UpdateApplicationDraftUseCase updateUseCase;
  @Mock private SubmitApplicationDraftV2UseCase submitUseCase;
  @Mock private ApplicationDraftV2CommandMapper mapper;
  @Mock private AuthenticatedCallerFactory authenticatedCallerFactory;

  @InjectMocks private ApplicationDraftV2CommandController controller;

  @BeforeEach
  void setUp() {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @ParameterizedTest
  @CsvSource({"true,201", "false,202"})
  void saveApplicationDraftReturnsStatusReflectingProjectionAndDraftVersion(
      boolean projected, int expectedStatus) {
    UUID applicationId = UUID.randomUUID();
    var request =
        new CreateApplicationDraftV2Request()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .laaReference("LAA-1")
            .applicationContent(Map.of("incomplete", true));
    var command =
        new CreateApplicationDraftV2Command(
            applicationId,
            "APPLICATION_IN_PROGRESS",
            "LAA-1",
            Map.of(),
            "{}",
            1,
            Instant.now(),
            List.of(),
            Map.of());
    when(mapper.toCreateCommand(request, 1)).thenReturn(command);
    when(createUseCase.execute(command)).thenReturn(new ApplicationDraftSaveResult(projected, 0L));

    ResponseEntity<SaveApplicationDraftV2Response> response =
        controller.saveApplicationDraftV2(null, request, 1);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.valueOf(expectedStatus));
    assertThat(response.getBody().getApplicationId()).isEqualTo(applicationId);
    assertThat(response.getBody().getDraftVersion()).isZero();
  }

  @Test
  void updateApplicationDraftReturnsOkWithNewDraftVersion() {
    UUID applicationId = UUID.randomUUID();
    var request =
        new UpdateApplicationDraftV2Request()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .laaReference("LAA-1")
            .applicationContent(Map.of("incomplete", true));
    var command =
        new UpdateApplicationDraftCommand(
            applicationId,
            "APPLICATION_IN_PROGRESS",
            "LAA-1",
            Map.of(),
            "{}",
            Instant.now(),
            List.of(),
            Map.of(),
            null);
    when(mapper.toUpdateCommand(applicationId, request, null)).thenReturn(command);
    when(updateUseCase.execute(command)).thenReturn(3L);

    ResponseEntity<SaveApplicationDraftV2Response> response =
        controller.updateApplicationDraftV2(null, applicationId, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getApplicationId()).isEqualTo(applicationId);
    assertThat(response.getBody().getDraftVersion()).isEqualTo(3L);
  }

  @ParameterizedTest
  @CsvSource({"true,200", "false,202"})
  void submitApplicationDraftReturnsStatusReflectingProjection(
      boolean projected, int expectedStatus) {
    UUID applicationId = UUID.randomUUID();
    var command = new SubmitApplicationDraftV2Command(applicationId, Instant.now());
    when(mapper.toSubmitCommand(applicationId)).thenReturn(command);
    when(submitUseCase.submit(command)).thenReturn(projected);

    ResponseEntity<SubmitApplicationDraftResponse> response =
        controller.submitApplicationDraftV2(null, applicationId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.valueOf(expectedStatus));
    assertThat(response.getBody().getApplicationId()).isEqualTo(applicationId);
  }
}

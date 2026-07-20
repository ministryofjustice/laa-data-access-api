package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.service.application.DraftApplicationService;
import uk.gov.justice.laa.dstew.access.service.application.SubmitApplicationService;

@ExtendWith(MockitoExtension.class)
class ApplicationCommandControllerTest {

  @Mock private DraftApplicationService draftApplicationService;
  @Mock private SubmitApplicationService submitApplicationService;

  @Test
  void givenBody_whenPutDraftApplication_thenReturns204AndDelegates() {
    UUID applicationId = UUID.randomUUID();
    Map<String, Object> content = Map.of("laaReference", "LAA-123");
    ApplicationCommandController controller = controller();

    ResponseEntity<Void> response = controller.putDraftApplication(applicationId, content);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(draftApplicationService).putDraft(applicationId, content);
  }

  @Test
  void givenStoredDraft_whenSubmitApplication_thenReturns204AndDelegates() {
    UUID applicationId = UUID.randomUUID();
    when(submitApplicationService.submit(applicationId, 2)).thenReturn(applicationId);
    ApplicationCommandController controller = controller();

    ResponseEntity<Void> response =
        controller.submitApplication(applicationId, ServiceName.CIVIL_APPLY, 2);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(submitApplicationService).submit(applicationId, 2);
  }

  @Test
  void givenNoDraft_whenSubmitApplication_thenMapsToResourceNotFound() {
    UUID applicationId = UUID.randomUUID();
    doThrow(new AggregateNotFoundException(applicationId.toString(), "missing"))
        .when(submitApplicationService)
        .submit(applicationId, 1);
    ApplicationCommandController controller = controller();

    assertThatThrownBy(
            () -> controller.submitApplication(applicationId, ServiceName.CIVIL_APPLY, 1))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("No draft application found with ID: " + applicationId);
  }

  private ApplicationCommandController controller() {
    return new ApplicationCommandController(draftApplicationService, submitApplicationService);
  }
}

package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.service.application.SubmitApplicationService;

@ExtendWith(MockitoExtension.class)
class ApplicationCommandControllerTest {

  @Mock private SubmitApplicationService submitApplicationService;

  @Test
  void givenValidRequest_whenPostApplication_thenReturns201WithLocation() {
    UUID applyApplicationId = UUID.randomUUID();
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    httpRequest.setRequestURI("/api/v0/applications");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));

    when(submitApplicationService.submit(any(ApplicationCreateRequest.class), eq(1)))
        .thenReturn(applyApplicationId);

    ApplicationCommandController controller =
        new ApplicationCommandController(submitApplicationService);

    ApplicationCreateRequest request =
        ApplicationCreateRequest.builder()
            .applicationType(ApplicationType.APPLY)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .build();

    ResponseEntity<Void> response =
        controller.createApplication(ServiceName.CIVIL_APPLY, 1, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().getPath())
        .endsWith("/api/v0/applications/" + applyApplicationId);

    RequestContextHolder.resetRequestAttributes();
  }
}

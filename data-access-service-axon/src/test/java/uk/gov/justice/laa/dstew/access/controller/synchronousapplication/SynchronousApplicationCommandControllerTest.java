package uk.gov.justice.laa.dstew.access.controller.synchronousapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.command.synchronousapplication.CreateSynchronousApplicationCommand;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

@ExtendWith(MockitoExtension.class)
class SynchronousApplicationCommandControllerTest {

  @Mock private CommandGateway commandGateway;

  private final CreateSynchronousApplicationCommandMapper commandMapper =
      new CreateSynchronousApplicationCommandMapper(JsonMapper.builder().build());

  @Test
  void givenValidRequest_whenPostSynchronousApplication_thenReturns201WithLocation() {
    UUID applyApplicationId = UUID.randomUUID();
    final UUID applyProceedingId = UUID.randomUUID();
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    httpRequest.setRequestURI("/api/v0/synchronous-applications");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));

    when(commandGateway.sendAndWait(Mockito.any(CreateSynchronousApplicationCommand.class)))
        .thenReturn(applyApplicationId);

    SynchronousApplicationCommandController controller =
        new SynchronousApplicationCommandController(commandGateway, commandMapper);

    ApplicationCreateRequest request =
        ApplicationCreateRequest.builder()
            .applicationType(ApplicationType.APPLY)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .applicationContent(
                Map.of(
                    "id",
                    applyApplicationId.toString(),
                    "submittedAt",
                    "2026-07-14T12:30:00Z",
                    "office",
                    Map.of("code", "1A001B"),
                    "applicant",
                    Map.of(
                        "id",
                        UUID.randomUUID().toString(),
                        "addresses",
                        java.util.List.of(Map.of("id", UUID.randomUUID().toString()))),
                    "proceedings",
                    java.util.List.of(
                        Map.of(
                            "id",
                            applyProceedingId.toString(),
                            "leadProceeding",
                            true,
                            "description",
                            "Care order",
                            "categoryOfLawEnum",
                            "FAMILY",
                            "matterTypeEnum",
                            "SPECIAL_CHILDREN_ACT",
                            "usedDelegatedFunctions",
                            false))))
            .build();

    ResponseEntity<Void> response =
        controller.createApplication(ServiceName.CIVIL_APPLY, 1, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().getPath())
        .endsWith("/api/v0/synchronous-applications/" + applyApplicationId);

    RequestContextHolder.resetRequestAttributes();
  }
}

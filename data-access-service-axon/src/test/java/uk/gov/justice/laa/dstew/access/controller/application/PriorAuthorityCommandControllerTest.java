package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityUseCase;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityRequest;
import uk.gov.justice.laa.dstew.access.model.CreatePriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

class PriorAuthorityCommandControllerTest {

  private CreatePriorAuthorityUseCase useCase;
  private CreatePriorAuthorityCommandMapper commandMapper;
  private PriorAuthorityCommandController controller;

  @BeforeEach
  void setUp() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v0/applications/" + UUID.randomUUID() + "/prior-authority");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    useCase = mock(CreatePriorAuthorityUseCase.class);
    commandMapper = mock(CreatePriorAuthorityCommandMapper.class);
    controller = new PriorAuthorityCommandController(useCase, commandMapper);
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void givenProjectionConfirmed_whenCreatePriorAuthority_thenReturns201WithBodyAndLocation() {
    UUID id = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-19T10:00:00Z");
    CreatePriorAuthorityCommand command = stubCommand(submissionId, id, occurredAt);
    when(commandMapper.toCommand(id, null)).thenReturn(command);
    when(useCase.execute(command)).thenReturn(true);

    ResponseEntity<CreatePriorAuthorityResponse> response =
        controller.createPriorAuthority(ServiceName.CIVIL_APPLY, id, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getSubmissionId()).isEqualTo(submissionId);
    assertThat(response.getBody().getSubmittedAt()).isEqualTo(occurredAt.atOffset(ZoneOffset.UTC));
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().toString()).endsWith("/" + submissionId);
  }

  @Test
  void givenProjectionTimeout_whenCreatePriorAuthority_thenReturns202WithBodyAndLocation() {
    UUID id = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-19T11:00:00Z");
    CreatePriorAuthorityCommand command = stubCommand(submissionId, id, occurredAt);
    when(commandMapper.toCommand(id, null)).thenReturn(command);
    when(useCase.execute(command)).thenReturn(false);

    ResponseEntity<CreatePriorAuthorityResponse> response =
        controller.createPriorAuthority(ServiceName.CIVIL_APPLY, id, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getSubmissionId()).isEqualTo(submissionId);
    assertThat(response.getBody().getSubmittedAt()).isEqualTo(occurredAt.atOffset(ZoneOffset.UTC));
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().toString()).endsWith("/" + submissionId);
  }

  @Test
  void givenRequest_whenCreatePriorAuthority_thenDelegatesToMapperAndUseCase() {
    UUID id = UUID.randomUUID();
    CreatePriorAuthorityRequest request = new CreatePriorAuthorityRequest();
    CreatePriorAuthorityCommand command = stubCommand(UUID.randomUUID(), id, Instant.now());
    when(commandMapper.toCommand(id, request)).thenReturn(command);
    when(useCase.execute(command)).thenReturn(true);

    controller.createPriorAuthority(null, id, request);

    verify(commandMapper).toCommand(id, request);
    verify(useCase).execute(command);
  }

  private CreatePriorAuthorityCommand stubCommand(
      UUID submissionId, UUID applicationId, Instant occurredAt) {
    return new CreatePriorAuthorityCommand(
        submissionId, applicationId, null, "{}", 1, "PriorAuthority.json", occurredAt);
  }
}

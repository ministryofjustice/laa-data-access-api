package uk.gov.justice.laa.dstew.access.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.command.application.SubmitApplicationCommand;
import uk.gov.justice.laa.dstew.access.exception.ConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.query.draft.DraftRecord;
import uk.gov.justice.laa.dstew.access.query.draft.DraftRepository;
import uk.gov.justice.laa.dstew.access.query.submission.SubmissionRecord;
import uk.gov.justice.laa.dstew.access.query.submission.SubmissionRepository;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

class SubmitApplicationServiceTest {

  private final DraftRepository draftRepository = mock(DraftRepository.class);
  private final ObjectMapper objectMapper = mock(ObjectMapper.class);
  private final ApplicationContentParser parser = mock(ApplicationContentParser.class);
  private final JsonSchemaValidator jsonSchemaValidator = mock(JsonSchemaValidator.class);
  private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
  private final CommandGateway commandGateway = mock(CommandGateway.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-15T08:00:00Z"), ZoneOffset.UTC);

  private final SubmitApplicationService service =
      new SubmitApplicationService(
          draftRepository,
          objectMapper,
          parser,
          jsonSchemaValidator,
          submissionRepository,
          commandGateway,
          clock);

  @Test
  void givenStoredDraft_whenSubmitted_thenSealsBodyAndDispatchesPointerCommand() {
    UUID applicationId = UUID.randomUUID();
    stubDraft(applicationId);
    // parser returns a different id — the aggregate id must be the path id, not the parsed one
    when(parser.parse(any())).thenReturn(parsed(UUID.randomUUID()));
    when(commandGateway.sendAndWait(any())).thenReturn(applicationId);

    UUID result = service.submit(applicationId, 2);

    assertThat(result).isEqualTo(applicationId);

    ArgumentCaptor<SubmissionRecord> recordCaptor = ArgumentCaptor.forClass(SubmissionRecord.class);
    verify(submissionRepository).save(recordCaptor.capture());
    SubmissionRecord saved = recordCaptor.getValue();
    assertThat(saved.getContentId()).isNotNull();
    assertThat(saved.getApplyApplicationId()).isEqualTo(applicationId);
    assertThat(saved.getData()).isNotNull();
    assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2026-07-15T08:00:00Z"));

    ArgumentCaptor<SubmitApplicationCommand> commandCaptor =
        ArgumentCaptor.forClass(SubmitApplicationCommand.class);
    verify(commandGateway).sendAndWait(commandCaptor.capture());
    SubmitApplicationCommand command = commandCaptor.getValue();
    assertThat(command.applyApplicationId()).isEqualTo(applicationId);
    assertThat(command.contentId()).isEqualTo(saved.getContentId());
    assertThat(command.schemaVersion()).isEqualTo(2);
    assertThat(command.laaReference()).isEqualTo("LAA-123");
    assertThat(command.officeCode()).isEqualTo("1A001B");
  }

  @Test
  void givenNoDraft_whenSubmitted_thenThrowsResourceNotFound() {
    UUID applicationId = UUID.randomUUID();
    when(draftRepository.findById(applicationId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submit(applicationId, 1))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(submissionRepository, never()).save(any());
    verify(commandGateway, never()).sendAndWait(any());
  }

  @Test
  void givenCommandRejected_whenSubmitted_thenCleansOrphanBodyAndRethrows() {
    UUID applicationId = UUID.randomUUID();
    stubDraft(applicationId);
    when(parser.parse(any())).thenReturn(parsed(applicationId));
    when(commandGateway.sendAndWait(any())).thenThrow(new ConflictException("already submitted"));

    assertThatThrownBy(() -> service.submit(applicationId, 1))
        .isInstanceOf(ConflictException.class);

    ArgumentCaptor<SubmissionRecord> recordCaptor = ArgumentCaptor.forClass(SubmissionRecord.class);
    verify(submissionRepository).save(recordCaptor.capture());
    verify(submissionRepository).deleteById(recordCaptor.getValue().getContentId());
  }

  private void stubDraft(UUID applicationId) {
    when(draftRepository.findById(applicationId))
        .thenReturn(
            Optional.of(
                DraftRecord.builder()
                    .applyApplicationId(applicationId)
                    .content(Map.of("laaReference", "LAA-123"))
                    .createdAt(Instant.parse("2026-07-14T08:00:00Z"))
                    .updatedAt(Instant.parse("2026-07-14T08:00:00Z"))
                    .build()));
    when(objectMapper.convertValue(any(), eq(ApplicationCreateRequest.class)))
        .thenReturn(
            ApplicationCreateRequest.builder()
                .applicationType(ApplicationType.APPLY)
                .laaReference("LAA-123")
                .applicationContent(Map.of("id", "x"))
                .build());
  }

  private ParsedAppContentDetails parsed(UUID applyApplicationId) {
    return ParsedAppContentDetails.builder()
        .applyApplicationId(applyApplicationId)
        .applicationContent(null)
        .proceedings(List.of())
        .submittedAt(Instant.parse("2026-07-14T12:30:00Z"))
        .officeCode("1A001B")
        .usedDelegatedFunctions(false)
        .build();
  }
}

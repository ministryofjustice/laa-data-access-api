package uk.gov.justice.laa.dstew.access.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.ConcurrencyException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.query.submission.SubmissionRecord;
import uk.gov.justice.laa.dstew.access.query.submission.SubmissionRepository;
import uk.gov.justice.laa.dstew.access.validation.DuplicateApplyApplicationIdException;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

class SubmitApplicationServiceTest {

  private final ApplicationContentParser parser = mock(ApplicationContentParser.class);
  private final JsonSchemaValidator jsonSchemaValidator = mock(JsonSchemaValidator.class);
  private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
  private final CommandGateway commandGateway = mock(CommandGateway.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-15T08:00:00Z"), ZoneOffset.UTC);

  private final SubmitApplicationService service =
      new SubmitApplicationService(
          parser, jsonSchemaValidator, submissionRepository, commandGateway, clock);

  @Test
  void givenValidRequest_whenSubmitted_thenStoresBodyAndDispatchesPointerCommand() {
    UUID applyApplicationId = UUID.randomUUID();
    when(parser.parse(any())).thenReturn(parsed(applyApplicationId));
    when(commandGateway.sendAndWait(any())).thenReturn(applyApplicationId);

    UUID result = service.submit(request(applyApplicationId), 1);

    assertThat(result).isEqualTo(applyApplicationId);

    ArgumentCaptor<SubmissionRecord> recordCaptor = ArgumentCaptor.forClass(SubmissionRecord.class);
    verify(submissionRepository).save(recordCaptor.capture());
    SubmissionRecord saved = recordCaptor.getValue();
    assertThat(saved.getContentId()).isNotNull();
    assertThat(saved.getApplyApplicationId()).isEqualTo(applyApplicationId);
    assertThat(saved.getData()).isNotNull();
    assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2026-07-15T08:00:00Z"));

    ArgumentCaptor<CreateApplicationCommand> commandCaptor =
        ArgumentCaptor.forClass(CreateApplicationCommand.class);
    verify(commandGateway).sendAndWait(commandCaptor.capture());
    CreateApplicationCommand command = commandCaptor.getValue();
    assertThat(command.applyApplicationId()).isEqualTo(applyApplicationId);
    assertThat(command.contentId()).isEqualTo(saved.getContentId());
    assertThat(command.laaReference()).isEqualTo("LAA-123");
    assertThat(command.officeCode()).isEqualTo("1A001B");
  }

  @Test
  void givenDuplicateApplication_whenSubmitted_thenCleansOrphanBodyAndThrows() {
    UUID applyApplicationId = UUID.randomUUID();
    when(parser.parse(any())).thenReturn(parsed(applyApplicationId));
    when(commandGateway.sendAndWait(any())).thenThrow(new ConcurrencyException("duplicate"));

    assertThatThrownBy(() -> service.submit(request(applyApplicationId), 1))
        .isInstanceOf(DuplicateApplyApplicationIdException.class);

    ArgumentCaptor<SubmissionRecord> recordCaptor = ArgumentCaptor.forClass(SubmissionRecord.class);
    verify(submissionRepository).save(recordCaptor.capture());
    verify(submissionRepository).deleteById(recordCaptor.getValue().getContentId());
  }

  private ApplicationCreateRequest request(UUID applyApplicationId) {
    return ApplicationCreateRequest.builder()
        .applicationType(ApplicationType.APPLY)
        .laaReference("LAA-123")
        .applicationContent(Map.of("id", applyApplicationId.toString()))
        .build();
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

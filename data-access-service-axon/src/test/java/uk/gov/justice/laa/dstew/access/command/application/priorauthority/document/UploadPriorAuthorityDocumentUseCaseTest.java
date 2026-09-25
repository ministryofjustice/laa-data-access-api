package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ValidateApplicationGrantedCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.exception.InvalidApplicationStateException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

@ExtendWith(MockitoExtension.class)
class UploadPriorAuthorityDocumentUseCaseTest {

  @Mock private PriorAuthorityDraftStore draftStore;
  @Mock private RetryingCommandDispatcher dispatcher;
  @Mock private SdsService sdsService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void givenDraftExists_whenExecute_thenValidatesApplicationAndDispatchesUploadCommand() {
    UploadPriorAuthorityDocumentUseCase useCase =
        new UploadPriorAuthorityDocumentUseCase(draftStore, dispatcher, sdsService, objectMapper);
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "%PDF-content".getBytes());

    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                new PriorAuthorityDataPayload(
                    priorAuthorityId,
                    applicationId,
                    new PriorAuthorityContent(null, null, null, null, null),
                    "{}",
                    java.time.Instant.now())));
    doNothing().when(dispatcher).dispatch(new ValidateApplicationGrantedCommand(applicationId));
    when(sdsService.savePriorAuthorityFile(
            org.mockito.ArgumentMatchers.eq(priorAuthorityId),
            org.mockito.ArgumentMatchers.any(UUID.class),
            org.mockito.ArgumentMatchers.eq(file)))
        .thenReturn(new DocumentUploadResponse().checksum("abc123"));
    doNothing()
        .when(dispatcher)
        .dispatch(org.mockito.ArgumentMatchers.any(PriorAuthorityDocumentUploadCommand.class));

    var response = useCase.execute(priorAuthorityId, file, "CIVIL_APPLY");

    assertThat(response.documentId()).isNotNull();
    assertThat(response.fileType()).isEqualTo("PDF");
    assertThat(response.contentType()).isEqualTo("application/pdf");
    assertThat(response.sourceService()).isEqualTo("CIVIL_APPLY");
    assertThat(response.checksum()).isEqualTo("abc123");
    verify(dispatcher).dispatch(new ValidateApplicationGrantedCommand(applicationId));
    verify(sdsService)
        .savePriorAuthorityFile(
            org.mockito.ArgumentMatchers.eq(priorAuthorityId),
            org.mockito.ArgumentMatchers.eq(response.documentId()),
            org.mockito.ArgumentMatchers.eq(file));
    ArgumentCaptor<PriorAuthorityDocumentUploadCommand> commandCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDocumentUploadCommand.class);
    verify(dispatcher).dispatch(commandCaptor.capture());
    assertThat(commandCaptor.getValue().fileType()).isEqualTo("PDF");
    assertThat(commandCaptor.getValue().contentType()).isEqualTo("application/pdf");
    assertThat(commandCaptor.getValue().serialisedRequest())
        .contains(
            "\"documentId\":\"%s\"".formatted(response.documentId()),
            "\"originalFilename\":\"evidence.pdf\"",
            "\"fileSize\":12",
            "\"fileType\":\"PDF\"",
            "\"contentType\":\"application/pdf\"",
            "\"sourceService\":\"CIVIL_APPLY\"",
            "\"checksum\":\"abc123\"");
  }

  @Test
  void givenDraftMissing_whenExecute_thenThrowsNotFound() {
    UploadPriorAuthorityDocumentUseCase useCase =
        new UploadPriorAuthorityDocumentUseCase(draftStore, dispatcher, sdsService, objectMapper);
    UUID priorAuthorityId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "%PDF-content".getBytes());

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(priorAuthorityId, file, "CIVIL_APPLY"));
  }

  @Test
  void givenFilenameWithoutExtension_whenExecute_thenRejectsBeforeUploadingToSds() {
    UploadPriorAuthorityDocumentUseCase useCase =
        new UploadPriorAuthorityDocumentUseCase(draftStore, dispatcher, sdsService, objectMapper);
    UUID priorAuthorityId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence", "application/pdf", "%PDF-content".getBytes());

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> useCase.execute(priorAuthorityId, file, "CIVIL_APPLY"))
        .withMessage("originalFilename must include a file extension");

    verifyNoInteractions(draftStore, dispatcher, sdsService);
  }

  @Test
  void givenSdsReturnsNull_whenExecute_thenDispatchesCommandWithNullChecksum() {
    UploadPriorAuthorityDocumentUseCase useCase =
        new UploadPriorAuthorityDocumentUseCase(draftStore, dispatcher, sdsService, objectMapper);
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "%PDF-content".getBytes());

    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                new PriorAuthorityDataPayload(
                    priorAuthorityId,
                    applicationId,
                    new PriorAuthorityContent(null, null, null, null, null),
                    "{}",
                    java.time.Instant.now())));
    doNothing().when(dispatcher).dispatch(new ValidateApplicationGrantedCommand(applicationId));
    when(sdsService.savePriorAuthorityFile(
            org.mockito.ArgumentMatchers.eq(priorAuthorityId),
            org.mockito.ArgumentMatchers.any(UUID.class),
            org.mockito.ArgumentMatchers.eq(file)))
        .thenReturn(null);
    doNothing()
        .when(dispatcher)
        .dispatch(org.mockito.ArgumentMatchers.any(PriorAuthorityDocumentUploadCommand.class));

    useCase.execute(priorAuthorityId, file, "CIVIL_APPLY");

    ArgumentCaptor<PriorAuthorityDocumentUploadCommand> commandCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDocumentUploadCommand.class);
    verify(dispatcher).dispatch(commandCaptor.capture());
    assertThat(commandCaptor.getValue().checksum()).isNull();
  }

  @Test
  void givenApplicationNotGranted_whenExecute_thenThrowsAndDoesNotDispatchUploadCommand() {
    UploadPriorAuthorityDocumentUseCase useCase =
        new UploadPriorAuthorityDocumentUseCase(draftStore, dispatcher, sdsService, objectMapper);
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "%PDF-content".getBytes());

    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                new PriorAuthorityDataPayload(
                    priorAuthorityId,
                    applicationId,
                    new PriorAuthorityContent(null, null, null, null, null),
                    "{}",
                    java.time.Instant.now())));
    doThrow(new InvalidApplicationStateException(applicationId, "IN_PROGRESS"))
        .when(dispatcher)
        .dispatch(new ValidateApplicationGrantedCommand(applicationId));

    assertThatExceptionOfType(InvalidApplicationStateException.class)
        .isThrownBy(() -> useCase.execute(priorAuthorityId, file, "CIVIL_APPLY"));

    verify(dispatcher).dispatch(new ValidateApplicationGrantedCommand(applicationId));
    verifyNoMoreInteractions(dispatcher);
  }

  @Test
  void givenNonPdfContent_whenExecute_thenRejectsBeforeUploadingToSds() {
    UploadPriorAuthorityDocumentUseCase useCase =
        new UploadPriorAuthorityDocumentUseCase(draftStore, dispatcher, sdsService, objectMapper);
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.txt", "text/plain", "not a PDF".getBytes());

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> useCase.execute(UUID.randomUUID(), file, "CIVIL_APPLY"))
        .withMessage("Unsupported document content type");

    verifyNoInteractions(sdsService);
  }

  @Test
  void givenPdfMimeWithInvalidSignature_whenExecute_thenRejectsBeforeUploadingToSds() {
    UploadPriorAuthorityDocumentUseCase useCase =
        new UploadPriorAuthorityDocumentUseCase(draftStore, dispatcher, sdsService, objectMapper);
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "not a PDF".getBytes());

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> useCase.execute(UUID.randomUUID(), file, "CIVIL_APPLY"))
        .withMessage("Uploaded file is not a valid PDF document");

    verifyNoInteractions(sdsService);
  }

  @Test
  void givenUnreadablePdf_whenExecute_thenRejectsBeforeUploadingToSds() throws IOException {
    UploadPriorAuthorityDocumentUseCase useCase =
        new UploadPriorAuthorityDocumentUseCase(draftStore, dispatcher, sdsService, objectMapper);
    MultipartFile file = mock(MultipartFile.class);
    when(file.getContentType()).thenReturn("application/pdf");
    when(file.getInputStream()).thenThrow(new IOException("Unable to read file"));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> useCase.execute(UUID.randomUUID(), file, "CIVIL_APPLY"))
        .withMessage("Unable to validate uploaded document")
        .withCauseInstanceOf(IOException.class);

    verifyNoInteractions(sdsService);
  }
}

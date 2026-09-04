package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.AttachPriorAuthorityDocumentCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ValidateApplicationGrantedCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.model.UploadPriorAuthorityDocumentResponse;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

@ExtendWith(MockitoExtension.class)
class UploadPriorAuthorityDocumentUseCaseTest {

  @Mock private RetryingCommandDispatcher dispatcher;
  @Mock private PriorAuthorityDraftStore draftStore;
  @Mock private SdsService sdsService;

  @InjectMocks private UploadPriorAuthorityDocumentUseCase useCase;

  @Test
  void givenDraftExists_whenExecute_thenValidatesUploadsAttachesAndReturnsDocumentId() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "test content".getBytes());
    DocumentUploadResponse sdsResponse = mock(DocumentUploadResponse.class);
    when(sdsResponse.getChecksum()).thenReturn("checksum-value");
    when(draftStore.find(priorAuthorityId))
        .thenReturn(Optional.of(stubDraftPayload(priorAuthorityId, applicationId)));
    when(sdsService.saveFile(eq(priorAuthorityId), any(UUID.class), eq(file)))
        .thenReturn(sdsResponse);

    UploadPriorAuthorityDocumentResponse response = useCase.execute(priorAuthorityId, file);

    assertThat(response.getDocumentId()).isNotNull();
    verify(dispatcher).dispatch(new ValidateApplicationGrantedCommand(applicationId));
    verify(sdsService).saveFile(eq(priorAuthorityId), eq(response.getDocumentId()), eq(file));

    AttachPriorAuthorityDocumentCommand attachCommand = captureAttachCommand(2);
    assertThat(attachCommand.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(attachCommand.documentId()).isEqualTo(response.getDocumentId());
    assertThat(attachCommand.fileName()).isEqualTo("evidence.pdf");
    assertThat(attachCommand.size()).isEqualTo(file.getSize());
    assertThat(attachCommand.extension()).isEqualTo("pdf");
    assertThat(attachCommand.contentType()).isEqualTo("application/pdf");
    assertThat(attachCommand.checksum()).isEqualTo("checksum-value");
    assertThat(attachCommand.occurredAt()).isNotNull();
  }

  @Test
  void givenNoDraftExists_whenExecute_thenThrowsResourceNotFoundAndNeverUploadsOrDispatches() {
    UUID priorAuthorityId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "test content".getBytes());
    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(priorAuthorityId, file))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Prior Authority %s not found".formatted(priorAuthorityId));

    verify(sdsService, never()).saveFile(any(), any(), any());
    verify(dispatcher, never()).dispatch(any());
  }

  @Test
  void givenFilenameWithNoExtension_whenExecute_thenAttachCommandHasNullExtension() {
    assertExtension("evidence", null);
  }

  @Test
  void givenFilenameEndingInDot_whenExecute_thenAttachCommandHasNullExtension() {
    assertExtension("evidence.", null);
  }

  @Test
  void givenNullFilename_whenExecute_thenAttachCommandHasNullExtension() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn(null);
    when(file.getContentType()).thenReturn("application/pdf");
    when(file.getSize()).thenReturn(7L);
    DocumentUploadResponse sdsResponse = mock(DocumentUploadResponse.class);
    when(draftStore.find(priorAuthorityId))
        .thenReturn(Optional.of(stubDraftPayload(priorAuthorityId, applicationId)));
    when(sdsService.saveFile(eq(priorAuthorityId), any(UUID.class), eq(file)))
        .thenReturn(sdsResponse);

    useCase.execute(priorAuthorityId, file);

    assertThat(captureAttachCommand(2).extension()).isNull();
  }

  private void assertExtension(String originalFilename, String expectedExtension) {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", originalFilename, "application/pdf", "content".getBytes());
    DocumentUploadResponse sdsResponse = mock(DocumentUploadResponse.class);
    when(draftStore.find(priorAuthorityId))
        .thenReturn(Optional.of(stubDraftPayload(priorAuthorityId, applicationId)));
    when(sdsService.saveFile(eq(priorAuthorityId), any(UUID.class), eq(file)))
        .thenReturn(sdsResponse);

    useCase.execute(priorAuthorityId, file);

    assertThat(captureAttachCommand(2).extension()).isEqualTo(expectedExtension);
  }

  private AttachPriorAuthorityDocumentCommand captureAttachCommand(int totalDispatches) {
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(dispatcher, times(totalDispatches)).dispatch(captor.capture());
    return captor.getAllValues().stream()
        .filter(AttachPriorAuthorityDocumentCommand.class::isInstance)
        .map(AttachPriorAuthorityDocumentCommand.class::cast)
        .findFirst()
        .orElseThrow();
  }

  private static PriorAuthorityDataPayload stubDraftPayload(
      UUID priorAuthorityId, UUID applicationId) {
    return new PriorAuthorityDataPayload(
        priorAuthorityId, applicationId, null, "{}", java.time.Instant.now());
  }
}

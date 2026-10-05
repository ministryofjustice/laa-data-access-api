package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeleteCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

@ExtendWith(MockitoExtension.class)
class DeleteEvidenceDocumentUseCaseTest {

  @Mock private PriorAuthorityDraftStore draftStore;
  @Mock private RetryingCommandDispatcher dispatcher;
  @Mock private SdsService sdsService;

  @Test
  void givenDraftExists_whenExecute_thenDispatchesBeforeDeletingTheSdsFile() {
    DeletePriorAuthorityDocumentUseCase useCase =
        new DeletePriorAuthorityDocumentUseCase(draftStore, dispatcher, sdsService);
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(draft(priorAuthorityId)));
    when(dispatcher.dispatch(
            any(PriorAuthorityDocumentDeleteCommand.class), eq(DocumentMetadata.class)))
        .thenReturn(pdfDocument(documentId));

    useCase.execute(priorAuthorityId, documentId);

    ArgumentCaptor<PriorAuthorityDocumentDeleteCommand> commandCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDocumentDeleteCommand.class);
    InOrder calls = inOrder(dispatcher, sdsService);
    calls.verify(dispatcher).dispatch(commandCaptor.capture(), eq(DocumentMetadata.class));
    calls.verify(sdsService).deleteFiles(priorAuthorityId, List.of(documentId.toString() + ".pdf"));
    assertThat(commandCaptor.getValue().priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(commandCaptor.getValue().documentId()).isEqualTo(documentId);
  }

  @Test
  void givenSdsDeletionFails_whenExecute_thenDoesNotPropagateTheFailure() {
    DeletePriorAuthorityDocumentUseCase useCase =
        new DeletePriorAuthorityDocumentUseCase(draftStore, dispatcher, sdsService);
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(draft(priorAuthorityId)));
    when(dispatcher.dispatch(
            any(PriorAuthorityDocumentDeleteCommand.class), eq(DocumentMetadata.class)))
        .thenReturn(pdfDocument(documentId));
    doThrow(new IllegalStateException("SDS unavailable"))
        .when(sdsService)
        .deleteFiles(priorAuthorityId, List.of(documentId.toString() + ".pdf"));

    useCase.execute(priorAuthorityId, documentId);

    verify(sdsService).deleteFiles(priorAuthorityId, List.of(documentId.toString() + ".pdf"));
  }

  @Test
  void givenDraftMissing_whenExecute_thenThrowsNotFoundWithoutDeletingFromSds() {
    DeletePriorAuthorityDocumentUseCase useCase =
        new DeletePriorAuthorityDocumentUseCase(draftStore, dispatcher, sdsService);
    UUID priorAuthorityId = UUID.randomUUID();
    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(priorAuthorityId, UUID.randomUUID()));

    verifyNoInteractions(dispatcher, sdsService);
  }

  private PriorAuthorityDataPayload draft(UUID priorAuthorityId) {
    return new PriorAuthorityDataPayload(
        priorAuthorityId,
        UUID.randomUUID(),
        new PriorAuthorityContent(null, null, null, null, null),
        "{}",
        Instant.now());
  }

  private DocumentMetadata pdfDocument(UUID documentId) {
    return new DocumentMetadata(
        documentId, null, Instant.now(), 1L, "application/pdf", null, "test", false);
  }
}

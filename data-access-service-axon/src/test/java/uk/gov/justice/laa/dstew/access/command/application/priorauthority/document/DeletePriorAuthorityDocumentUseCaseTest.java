package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeleteCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.UploadedDocumentData;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

@ExtendWith(MockitoExtension.class)
class DeletePriorAuthorityDocumentUseCaseTest {

  @Mock private PriorAuthorityDraftStore draftStore;
  @Mock private RetryingCommandDispatcher dispatcher;
  @Mock private SdsService sdsService;
  @Mock private SubscriptionProjectionGateway projectionGateway;

  @Test
  void givenProjectedDocument_whenExecute_thenUsesEventSourcedFileTypeForSdsKey() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                new PriorAuthorityDataPayload(
                    priorAuthorityId, UUID.randomUUID(), null, "{}", Instant.now())));
    when(projectionGateway.findProjection(any(), any()))
        .thenReturn(Optional.of(document(documentId)));
    when(projectionGateway.awaitProjection(
            any(), (java.util.function.Predicate<Boolean>) any(), any()))
        .thenAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(2)).run();
              return true;
            });
    DeletePriorAuthorityDocumentUseCase useCase =
        new DeletePriorAuthorityDocumentUseCase(
            draftStore, dispatcher, sdsService, new ObjectMapper(), projectionGateway);

    assertThat(useCase.execute(priorAuthorityId, documentId)).isTrue();

    ArgumentCaptor<PriorAuthorityDocumentDeleteCommand> command =
        ArgumentCaptor.forClass(PriorAuthorityDocumentDeleteCommand.class);
    verify(dispatcher).dispatch(command.capture());
    assertThat(command.getValue().documentId()).isEqualTo(documentId);
    verify(sdsService).deleteFiles(priorAuthorityId, List.of(documentId + ".pdf"));
  }

  @Test
  void givenDraftMissing_whenExecute_thenThrowsResourceNotFound() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());
    DeletePriorAuthorityDocumentUseCase useCase =
        new DeletePriorAuthorityDocumentUseCase(
            draftStore, dispatcher, sdsService, new ObjectMapper(), projectionGateway);

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(priorAuthorityId, documentId));

    verifyNoInteractions(dispatcher, sdsService, projectionGateway);
  }

  @Test
  void givenProjectionNeverFindsDocument_whenExecute_thenThrowsResourceNotFound() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                new PriorAuthorityDataPayload(
                    priorAuthorityId, UUID.randomUUID(), null, "{}", Instant.now())));
    when(projectionGateway.findProjection(any(), any())).thenReturn(Optional.empty());
    when(projectionGateway.awaitProjection(
            any(), (java.util.function.Predicate<Boolean>) any(), any()))
        .thenAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(2)).run();
              return true;
            });
    DeletePriorAuthorityDocumentUseCase useCase =
        new DeletePriorAuthorityDocumentUseCase(
            draftStore, dispatcher, sdsService, new ObjectMapper(), projectionGateway);

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(priorAuthorityId, documentId));

    verifyNoInteractions(sdsService);
  }

  @Test
  void givenSdsDeletionFails_whenExecute_thenStillReturnsProjectionResult() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                new PriorAuthorityDataPayload(
                    priorAuthorityId, UUID.randomUUID(), null, "{}", Instant.now())));
    when(projectionGateway.findProjection(any(), any()))
        .thenReturn(Optional.of(document(documentId)));
    when(projectionGateway.awaitProjection(
            any(), (java.util.function.Predicate<Boolean>) any(), any()))
        .thenAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(2)).run();
              return true;
            });
    doThrow(new RuntimeException("SDS unavailable")).when(sdsService).deleteFiles(any(), any());
    DeletePriorAuthorityDocumentUseCase useCase =
        new DeletePriorAuthorityDocumentUseCase(
            draftStore, dispatcher, sdsService, new ObjectMapper(), projectionGateway);

    assertThat(useCase.execute(priorAuthorityId, documentId)).isTrue();
  }

  private UploadedDocumentData document(UUID documentId) {
    return new UploadedDocumentData(
        documentId, 1L, "PDF", "application/pdf", "test", null, Instant.now(), null);
  }
}

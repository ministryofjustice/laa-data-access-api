package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
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
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeleteCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

@ExtendWith(MockitoExtension.class)
class DeletePriorAuthorityDocumentUseCaseTest {

  @Mock private PriorAuthorityDraftStore draftStore;
  @Mock private UploadedDocumentStore uploadedDocumentStore;
  @Mock private RetryingCommandDispatcher dispatcher;
  @Mock private SdsService sdsService;
  @Mock private SubscriptionProjectionGateway projectionGateway;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    lenient()
        .when(
            projectionGateway.awaitProjection(
                any(), (java.util.function.Predicate<Boolean>) any(), any()))
        .thenAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(2)).run();
              return true;
            });
  }

  @Test
  void givenDraftExists_whenExecute_thenDispatchesBeforeDeletingTheSdsFile() {
    DeletePriorAuthorityDocumentUseCase useCase =
        new DeletePriorAuthorityDocumentUseCase(
            draftStore,
            uploadedDocumentStore,
            dispatcher,
            sdsService,
            new ObjectMapper(),
            projectionGateway);
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                new PriorAuthorityDataPayload(
                    priorAuthorityId,
                    applicationId,
                    contentWithPdfDocument(documentId),
                    "{}",
                    Instant.now())));
    when(uploadedDocumentStore.findAllInOrder(List.of(documentId)))
        .thenReturn(List.of(storedDocument(documentId)));

    useCase.execute(priorAuthorityId, documentId);

    ArgumentCaptor<PriorAuthorityDocumentDeleteCommand> commandCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDocumentDeleteCommand.class);
    verify(dispatcher).dispatch(commandCaptor.capture());
    assertThat(commandCaptor.getValue().priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(commandCaptor.getValue().documentId()).isEqualTo(documentId);
    assertThat(commandCaptor.getValue().serialisedRequest())
        .contains("\"documentId\":\"%s\"".formatted(documentId));
    InOrder calls = inOrder(dispatcher, sdsService);
    calls.verify(dispatcher).dispatch(any(PriorAuthorityDocumentDeleteCommand.class));
    calls.verify(sdsService).deleteFiles(priorAuthorityId, List.of(documentId.toString() + ".pdf"));
  }

  @Test
  void givenSdsDeletionFails_whenExecute_thenDoesNotPropagateTheFailure() {
    DeletePriorAuthorityDocumentUseCase useCase =
        new DeletePriorAuthorityDocumentUseCase(
            draftStore,
            uploadedDocumentStore,
            dispatcher,
            sdsService,
            new ObjectMapper(),
            projectionGateway);
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                new PriorAuthorityDataPayload(
                    priorAuthorityId,
                    applicationId,
                    contentWithPdfDocument(documentId),
                    "{}",
                    Instant.now())));
    when(uploadedDocumentStore.findAllInOrder(List.of(documentId)))
        .thenReturn(List.of(storedDocument(documentId)));
    doThrow(new IllegalStateException("SDS unavailable"))
        .when(sdsService)
        .deleteFiles(priorAuthorityId, List.of(documentId.toString() + ".pdf"));

    useCase.execute(priorAuthorityId, documentId);

    verify(dispatcher).dispatch(any(PriorAuthorityDocumentDeleteCommand.class));
    verify(sdsService).deleteFiles(priorAuthorityId, List.of(documentId.toString() + ".pdf"));
  }

  @Test
  void givenDraftMissing_whenExecute_thenThrowsNotFoundWithoutDeletingFromSds() {
    DeletePriorAuthorityDocumentUseCase useCase =
        new DeletePriorAuthorityDocumentUseCase(
            draftStore,
            uploadedDocumentStore,
            dispatcher,
            sdsService,
            new ObjectMapper(),
            projectionGateway);
    UUID priorAuthorityId = UUID.randomUUID();
    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(priorAuthorityId, UUID.randomUUID()));

    verifyNoInteractions(dispatcher, sdsService);
  }

  @Test
  void givenDocumentMissing_whenExecute_thenThrowsNotFoundWithoutDeletingFromSds() {
    DeletePriorAuthorityDocumentUseCase useCase =
        new DeletePriorAuthorityDocumentUseCase(
            draftStore,
            uploadedDocumentStore,
            dispatcher,
            sdsService,
            new ObjectMapper(),
            projectionGateway);
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                new PriorAuthorityDataPayload(
                    priorAuthorityId,
                    applicationId,
                    contentWithPdfDocument(documentId),
                    "{}",
                    Instant.now())));
    when(uploadedDocumentStore.findAllInOrder(List.of(documentId))).thenReturn(List.of());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(priorAuthorityId, documentId));

    verifyNoInteractions(dispatcher, sdsService);
  }

  private PriorAuthorityDocument document(UUID documentId) {
    return new PriorAuthorityDocument(
        documentId,
        null,
        "evidence.pdf",
        "PDF",
        "application/pdf",
        1L,
        Instant.now(),
        "test",
        null);
  }

  private UploadedDocument storedDocument(UUID documentId) {
    return UploadedDocument.builder()
        .documentId(documentId)
        .originalFilename("evidence.pdf")
        .build();
  }

  private PriorAuthorityContent contentWithPdfDocument(UUID documentId) {
    return new PriorAuthorityContent(null, null, null, null, null, List.of(document(documentId)));
  }
}

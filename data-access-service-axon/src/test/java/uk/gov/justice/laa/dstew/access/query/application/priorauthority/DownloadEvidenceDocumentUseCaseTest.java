package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import uk.gov.justice.laa.dstew.access.content.priorauthority.EvidenceDocument;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
import uk.gov.justice.laa.dstew.access.usecase.application.priorauthority.DownloadPriorAuthorityDocumentUseCase;

@ExtendWith(MockitoExtension.class)
class DownloadEvidenceDocumentUseCaseTest {

  @Mock private QueryGateway queryGateway;
  @Mock private SdsService sdsService;

  @InjectMocks private DownloadPriorAuthorityDocumentUseCase useCase;

  @Test
  void givenOwnedDocument_whenDownloaded_thenRetrievesItsContentFromSds() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant uploadedAt = Instant.parse("2026-09-08T12:00:00Z");
    EvidenceDocument document = document(documentId, uploadedAt, "evidence.pdf");
    Resource resource = org.mockito.Mockito.mock(Resource.class);
    givenQueryReturns(priorAuthorityId, documentId, document);
    when(sdsService.getEvidenceFile(priorAuthorityId, documentId, "evidence.pdf"))
        .thenReturn(resource);

    EvidenceDocumentDownload download = useCase.downloadDocument(priorAuthorityId, documentId);

    assertThat(download.document())
        .isEqualTo(
            new EvidenceDocument(
                documentId,
                "GATEWAY_EVIDENCE",
                "evidence.pdf",
                "PDF",
                "application/pdf",
                123L,
                uploadedAt,
                "civil-apply",
                "checksum"));
    assertThat(download.resource()).isSameAs(resource);
  }

  @Test
  void givenSameNamedDocuments_whenOneIsDownloaded_thenUsesItsIndependentDocumentId() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID secondDocumentId = UUID.randomUUID();
    EvidenceDocument document = document(secondDocumentId, Instant.now(), "evidence.pdf");
    Resource resource = org.mockito.Mockito.mock(Resource.class);
    givenQueryReturns(priorAuthorityId, secondDocumentId, document);
    when(sdsService.getEvidenceFile(priorAuthorityId, secondDocumentId, "evidence.pdf"))
        .thenReturn(resource);

    EvidenceDocumentDownload download =
        useCase.downloadDocument(priorAuthorityId, secondDocumentId);

    assertThat(download.document().documentId()).isEqualTo(secondDocumentId);
    assertThat(download.resource()).isSameAs(resource);
    verify(sdsService).getEvidenceFile(priorAuthorityId, secondDocumentId, "evidence.pdf");
  }

  @Test
  void givenUnownedOrDeletedDocument_whenDownloaded_thenThrowsNotFound() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    givenQueryReturns(priorAuthorityId, documentId, null);

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.downloadDocument(priorAuthorityId, documentId))
        .withMessage(
            "No document found with ID: %s for prior authority: %s", documentId, priorAuthorityId);
    verifyNoInteractions(sdsService);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void givenActiveDocumentWithNoFilename_whenDownloaded_thenRetrievesFromSds(String filename) {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    EvidenceDocument document = document(documentId, Instant.now(), filename);
    Resource resource = org.mockito.Mockito.mock(Resource.class);
    givenQueryReturns(priorAuthorityId, documentId, document);
    when(sdsService.getEvidenceFile(priorAuthorityId, documentId, filename)).thenReturn(resource);

    EvidenceDocumentDownload download = useCase.downloadDocument(priorAuthorityId, documentId);

    assertThat(download.document().fileName()).isEqualTo(filename);
    assertThat(download.resource()).isSameAs(resource);
  }

  private void givenQueryReturns(
      UUID priorAuthorityId, UUID documentId, EvidenceDocument document) {
    when(queryGateway.query(
            new FindPriorAuthorityDocumentQuery(priorAuthorityId, documentId),
            EvidenceDocument.class))
        .thenReturn(CompletableFuture.completedFuture(document));
  }

  @ParameterizedTest
  @ValueSource(strings = {".pdf", ".PDF", ".png", ""})
  void givenSuffixWithoutFilename_whenDownloaded_thenUsesPersistedKey(String suffix) {
    UUID ownerId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    EvidenceDocument document =
        new EvidenceDocument(
            documentId,
            null,
            null,
            null,
            "application/pdf",
            12L,
            Instant.now(),
            "CIVIL_APPLY",
            null,
            suffix);
    givenQueryReturns(ownerId, documentId, document);
    Resource resource = org.mockito.Mockito.mock(Resource.class);
    when(sdsService.getEvidenceFile(ownerId, documentId, documentId + suffix)).thenReturn(resource);

    assertThat(useCase.downloadDocument(ownerId, documentId).resource()).isSameAs(resource);
  }

  private EvidenceDocument document(UUID documentId, Instant uploadedAt, String filename) {
    return new EvidenceDocument(
        documentId,
        "GATEWAY_EVIDENCE",
        filename,
        "PDF",
        "application/pdf",
        123L,
        uploadedAt,
        "civil-apply",
        "checksum");
  }
}

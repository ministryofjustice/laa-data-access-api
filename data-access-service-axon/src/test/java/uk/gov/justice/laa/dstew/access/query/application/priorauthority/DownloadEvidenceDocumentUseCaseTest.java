package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import uk.gov.justice.laa.dstew.access.command.application.UploadDocument;
import uk.gov.justice.laa.dstew.access.content.priorauthority.EvidenceDocument;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
import uk.gov.justice.laa.dstew.access.usecase.application.priorauthority.DownloadPriorAuthorityDocumentUseCase;
import uk.gov.justice.laa.dstew.access.usecase.application.priorauthority.GetPriorAuthorityUseCase;

@ExtendWith(MockitoExtension.class)
class DownloadEvidenceDocumentUseCaseTest {

  @Mock private GetPriorAuthorityUseCase getPriorAuthorityUseCase;
  @Mock private SdsService sdsService;

  @InjectMocks private DownloadPriorAuthorityDocumentUseCase useCase;

  @Test
  void givenOwnedDocument_whenDownloaded_thenRetrievesItsContentFromSds() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant uploadedAt = Instant.parse("2026-09-08T12:00:00Z");
    PriorAuthorityResult result =
        PriorAuthorityResult.builder()
            .uploadedDocuments(List.of(document(documentId, uploadedAt, false)))
            .documentFilenames(Map.of(documentId, "evidence.pdf"))
            .build();
    Resource resource = org.mockito.Mockito.mock(Resource.class);
    when(getPriorAuthorityUseCase.getPriorAuthority(priorAuthorityId)).thenReturn(result);
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
    UUID firstDocumentId = UUID.randomUUID();
    UUID secondDocumentId = UUID.randomUUID();
    PriorAuthorityResult result =
        PriorAuthorityResult.builder()
            .uploadedDocuments(
                List.of(
                    document(firstDocumentId, Instant.now(), false),
                    document(secondDocumentId, Instant.now(), false)))
            .documentFilenames(
                Map.of(firstDocumentId, "evidence.pdf", secondDocumentId, "evidence.pdf"))
            .build();
    Resource resource = org.mockito.Mockito.mock(Resource.class);
    when(getPriorAuthorityUseCase.getPriorAuthority(priorAuthorityId)).thenReturn(result);
    when(sdsService.getEvidenceFile(priorAuthorityId, secondDocumentId, "evidence.pdf"))
        .thenReturn(resource);

    EvidenceDocumentDownload download =
        useCase.downloadDocument(priorAuthorityId, secondDocumentId);

    assertThat(download.document().documentId()).isEqualTo(secondDocumentId);
    assertThat(download.resource()).isSameAs(resource);
    verify(sdsService).getEvidenceFile(priorAuthorityId, secondDocumentId, "evidence.pdf");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void givenUnownedOrDeletedDocument_whenDownloaded_thenThrowsNotFound(boolean deleted) {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(getPriorAuthorityUseCase.getPriorAuthority(priorAuthorityId))
        .thenReturn(
            PriorAuthorityResult.builder()
                .uploadedDocuments(
                    deleted ? List.of(document(documentId, Instant.now(), true)) : List.of())
                .build());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.downloadDocument(priorAuthorityId, documentId))
        .withMessage(
            "No document found with ID: %s for prior authority: %s", documentId, priorAuthorityId);
    verifyNoInteractions(sdsService);
  }

  @Test
  void givenNullDocumentList_whenDownloaded_thenThrowsNotFound() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(getPriorAuthorityUseCase.getPriorAuthority(priorAuthorityId))
        .thenReturn(PriorAuthorityResult.builder().uploadedDocuments(null).build());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.downloadDocument(priorAuthorityId, documentId));
    verifyNoInteractions(sdsService);
  }

  @Test
  void givenActiveDocumentWithNoFilenameOrKnownContentType_whenDownloaded_thenReturnsNullFields() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    UploadDocument document =
        new UploadDocument(
            documentId,
            null,
            Instant.now(),
            123L,
            "application/octet-stream",
            "checksum",
            "civil-apply",
            false);
    PriorAuthorityResult result =
        PriorAuthorityResult.builder().uploadedDocuments(List.of(document)).build();
    Resource resource = org.mockito.Mockito.mock(Resource.class);
    when(getPriorAuthorityUseCase.getPriorAuthority(priorAuthorityId)).thenReturn(result);
    when(sdsService.getEvidenceFile(priorAuthorityId, documentId, null)).thenReturn(resource);

    EvidenceDocumentDownload download =
        useCase.downloadDocument(priorAuthorityId, documentId);

    assertThat(download.document().fileName()).isNull();
    assertThat(download.document().fileType()).isNull();
    assertThat(download.resource()).isSameAs(resource);
  }

  @Test
  void givenDifferentActiveDocument_whenDownloaded_thenThrowsNotFound() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID requestedDocumentId = UUID.randomUUID();
    PriorAuthorityResult result =
        PriorAuthorityResult.builder()
            .uploadedDocuments(List.of(document(UUID.randomUUID(), Instant.now(), false)))
            .build();
    when(getPriorAuthorityUseCase.getPriorAuthority(priorAuthorityId)).thenReturn(result);

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.downloadDocument(priorAuthorityId, requestedDocumentId));
    verifyNoInteractions(sdsService);
  }

  private UploadDocument document(UUID documentId, Instant uploadedAt, boolean deleted) {
    return new UploadDocument(
        documentId,
        "GATEWAY_EVIDENCE",
        uploadedAt,
        123L,
        "application/pdf",
        "checksum",
        "civil-apply",
        deleted);
  }
}

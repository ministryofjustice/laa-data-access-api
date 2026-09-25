package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

@ExtendWith(MockitoExtension.class)
class DownloadPriorAuthorityDocumentUseCaseTest {

  @Mock private GetPriorAuthorityUseCase getPriorAuthorityUseCase;
  @Mock private SdsService sdsService;

  @InjectMocks private DownloadPriorAuthorityDocumentUseCase useCase;

  @Test
  void givenOwnedDocument_whenDownloaded_thenRetrievesItsContentFromSds() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityDocument document = document(documentId);
    PriorAuthorityResult result =
        PriorAuthorityResult.builder().uploadedDocuments(List.of(document)).build();
    Resource resource = org.mockito.Mockito.mock(Resource.class);
    when(getPriorAuthorityUseCase.getPriorAuthority(priorAuthorityId)).thenReturn(result);
    when(sdsService.getEvidenceFile(priorAuthorityId, documentId, document.fileName()))
        .thenReturn(resource);

    PriorAuthorityDocumentDownload download =
        useCase.downloadDocument(priorAuthorityId, documentId);

    assertThat(download.document()).isSameAs(document);
    assertThat(download.resource()).isSameAs(resource);
    verify(sdsService).getEvidenceFile(priorAuthorityId, documentId, document.fileName());
  }

  @Test
  void givenSameNamedDocuments_whenOneIsDownloaded_thenUsesItsIndependentDocumentId() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID firstDocumentId = UUID.randomUUID();
    UUID secondDocumentId = UUID.randomUUID();
    PriorAuthorityDocument firstDocument = document(firstDocumentId);
    PriorAuthorityDocument secondDocument = document(secondDocumentId);
    PriorAuthorityResult result =
        PriorAuthorityResult.builder()
            .uploadedDocuments(List.of(firstDocument, secondDocument))
            .build();
    Resource resource = org.mockito.Mockito.mock(Resource.class);
    when(getPriorAuthorityUseCase.getPriorAuthority(priorAuthorityId)).thenReturn(result);
    when(sdsService.getEvidenceFile(priorAuthorityId, secondDocumentId, "evidence.pdf"))
        .thenReturn(resource);

    PriorAuthorityDocumentDownload download =
        useCase.downloadDocument(priorAuthorityId, secondDocumentId);

    assertThat(download.document()).isSameAs(secondDocument);
    assertThat(download.resource()).isSameAs(resource);
    verify(sdsService).getEvidenceFile(priorAuthorityId, secondDocumentId, "evidence.pdf");
  }

  @Test
  void givenUnownedDocument_whenDownloaded_thenThrowsNotFound() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(getPriorAuthorityUseCase.getPriorAuthority(priorAuthorityId))
        .thenReturn(PriorAuthorityResult.builder().uploadedDocuments(List.of()).build());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.downloadDocument(priorAuthorityId, documentId))
        .withMessage(
            "No document found with ID: %s for prior authority: %s", documentId, priorAuthorityId);
  }

  private PriorAuthorityDocument document(UUID documentId) {
    return new PriorAuthorityDocument(
        documentId,
        null,
        "evidence.pdf",
        "PDF",
        "application/pdf",
        123L,
        Instant.now(),
        "civil-apply",
        null);
  }
}

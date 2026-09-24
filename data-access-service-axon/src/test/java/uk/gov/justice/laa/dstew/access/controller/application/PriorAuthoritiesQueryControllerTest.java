package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityResponse;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.DownloadPriorAuthorityDocumentUseCase;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.GetPriorAuthorityUseCase;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityDocumentDownload;

/** Verifies that each controller endpoint delegates to the appropriate use case and mapper. */
@ExtendWith(MockitoExtension.class)
class PriorAuthoritiesQueryControllerTest {

  @Mock private GetPriorAuthorityUseCase getPriorAuthorityUseCase;
  @Mock private DownloadPriorAuthorityDocumentUseCase downloadPriorAuthorityDocumentUseCase;
  @Mock private GetPriorAuthorityResponseMapper getPriorAuthorityResponseMapper;

  @InjectMocks private PriorAuthoritiesQueryController controller;

  @Test
  void givenExistingPriorAuthority_whenGetPriorAuthority_thenDelegatesToUseCaseAndMapper() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityResult result = mock(PriorAuthorityResult.class);
    PriorAuthorityResponse response = new PriorAuthorityResponse();
    when(getPriorAuthorityUseCase.getPriorAuthority(priorAuthorityId)).thenReturn(result);
    when(getPriorAuthorityResponseMapper.toResponse(result)).thenReturn(response);

    ResponseEntity<PriorAuthorityResponse> actual =
        controller.getPriorAuthority(null, priorAuthorityId);

    assertThat(actual.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(actual.getBody()).isSameAs(response);
    verify(getPriorAuthorityUseCase).getPriorAuthority(priorAuthorityId);
  }

  @Test
  void givenOwnedDocument_whenDownloaded_thenUsesOriginalFilenameAndMetadata() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityDocument document = document(documentId, "application/pdf", 123L);
    Resource resource = mock(Resource.class);
    when(downloadPriorAuthorityDocumentUseCase.downloadDocument(priorAuthorityId, documentId))
        .thenReturn(new PriorAuthorityDocumentDownload(document, resource));

    ResponseEntity<Resource> response =
        controller.downloadPriorAuthorityDocument(null, priorAuthorityId, documentId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isSameAs(resource);
    assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
    assertThat(response.getHeaders().getContentLength()).isEqualTo(123L);
    assertThat(response.getHeaders().getFirst("Content-Disposition"))
        .contains("attachment")
        .contains("original evidence.pdf");
    verify(downloadPriorAuthorityDocumentUseCase).downloadDocument(priorAuthorityId, documentId);
  }

  @Test
  void givenDocumentWithoutMediaTypeOrSize_whenDownloaded_thenUsesDefaultMediaType() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Resource resource = mock(Resource.class);
    when(downloadPriorAuthorityDocumentUseCase.downloadDocument(priorAuthorityId, documentId))
        .thenReturn(new PriorAuthorityDocumentDownload(document(documentId, null, null), resource));

    ResponseEntity<Resource> response =
        controller.downloadPriorAuthorityDocument(null, priorAuthorityId, documentId);

    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    assertThat(response.getHeaders().getContentLength()).isNegative();
  }

  @Test
  void givenDocumentWithInvalidMediaType_whenDownloaded_thenUsesDefaultMediaType() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityDocument document = document(documentId, "invalid media type", 123L);
    Resource resource = mock(Resource.class);
    when(downloadPriorAuthorityDocumentUseCase.downloadDocument(priorAuthorityId, documentId))
        .thenReturn(new PriorAuthorityDocumentDownload(document, resource));

    ResponseEntity<Resource> response =
        controller.downloadPriorAuthorityDocument(null, priorAuthorityId, documentId);

    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
  }

  private PriorAuthorityDocument document(UUID documentId, String mediaType, Long size) {
    return new PriorAuthorityDocument(
        documentId,
        null,
        "original evidence.pdf",
        "PDF",
        mediaType,
        size,
        Instant.now(),
        "civil-apply",
        null);
  }
}

package uk.gov.justice.laa.dstew.access.usecase.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import uk.gov.justice.laa.dstew.access.content.priorauthority.EvidenceDocument;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationDocumentQuery;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.EvidenceDocumentDownload;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

@ExtendWith(MockitoExtension.class)
class DownloadApplicationDocumentUseCaseTest {

  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final UUID DOCUMENT_ID = UUID.randomUUID();

  @Mock private QueryGateway queryGateway;
  @Mock private SdsService sdsService;

  @InjectMocks private DownloadApplicationDocumentUseCase useCase;

  @Test
  void givenProjectedDocument_whenDownloaded_thenStreamsItsContentFromSds() {
    EvidenceDocument document =
        new EvidenceDocument(
            DOCUMENT_ID,
            "GATEWAY_EVIDENCE",
            "original evidence.pdf",
            null,
            "application/pdf",
            12L,
            Instant.parse("2026-09-28T15:10:27.430Z"),
            "CIVIL_APPLY",
            "checksum");
    givenQueryReturns(document);
    Resource resource = mock(Resource.class);
    when(sdsService.getEvidenceFile(APPLICATION_ID, DOCUMENT_ID, "original evidence.pdf"))
        .thenReturn(resource);

    EvidenceDocumentDownload download = useCase.downloadDocument(APPLICATION_ID, DOCUMENT_ID);

    assertThat(download.document()).isSameAs(document);
    assertThat(download.resource()).isSameAs(resource);
  }

  @Test
  void givenNoProjectedDocument_whenDownloaded_thenThrowsNotFoundWithoutCallingSds() {
    givenQueryReturns(null);

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.downloadDocument(APPLICATION_ID, DOCUMENT_ID))
        .withMessageContaining(DOCUMENT_ID.toString());
    verifyNoInteractions(sdsService);
  }

  private void givenQueryReturns(EvidenceDocument document) {
    when(queryGateway.query(
            new FindApplicationDocumentQuery(APPLICATION_ID, DOCUMENT_ID), EvidenceDocument.class))
        .thenReturn(CompletableFuture.completedFuture(document));
  }
}

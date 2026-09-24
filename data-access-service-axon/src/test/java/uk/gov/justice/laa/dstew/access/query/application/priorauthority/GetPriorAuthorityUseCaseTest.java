package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

@ExtendWith(MockitoExtension.class)
class GetPriorAuthorityUseCaseTest {

  @Mock private QueryGateway queryGateway;
  @Mock private SdsService sdsService;

  @InjectMocks private GetPriorAuthorityUseCase useCase;

  @Test
  void givenProjectedPriorAuthority_whenRetrieved_thenReturnsProjectionResult() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityResult projectedResult =
        PriorAuthorityResult.builder()
            .priorAuthorityId(priorAuthorityId)
            .applicationId(applicationId)
            .justification("Counsel is required")
            .status("SUBMITTED")
            .priorAuthorityType(PriorAuthorityType.COUNSEL)
            .counselDetails(
                new uk.gov.justice.laa.dstew.access.content.priorauthority.CounselDetails(
                    uk.gov.justice.laa.dstew.access.content.priorauthority.CounselType
                        .TWO_JUNIOR_COUNSEL))
            .build();
    when(queryGateway.query(
            any(FindPriorAuthorityByPriorAuthorityIdQuery.class), eq(PriorAuthorityResult.class)))
        .thenReturn(CompletableFuture.completedFuture(projectedResult));

    PriorAuthorityResult response = useCase.getPriorAuthority(priorAuthorityId);

    assertThat(response).isSameAs(projectedResult);
    verify(queryGateway)
        .query(
            eq(new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId)),
            eq(PriorAuthorityResult.class));
  }

  @Test
  void givenUnknownPriorAuthority_whenRetrieved_thenThrowsNotFound() {
    UUID priorAuthorityId = UUID.randomUUID();
    when(queryGateway.query(
            any(FindPriorAuthorityByPriorAuthorityIdQuery.class), eq(PriorAuthorityResult.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.getPriorAuthority(priorAuthorityId))
        .withMessage("No prior authority found with ID: " + priorAuthorityId);
  }

  @Test
  void givenOwnedDocument_whenRetrieved_thenReturnsDocumentMetadata() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityDocument document =
        new PriorAuthorityDocument(
            documentId,
            null,
            "evidence.pdf",
            "PDF",
            "application/pdf",
            123L,
            Instant.now(),
            "civil-apply",
            null);
    PriorAuthorityResult result =
        PriorAuthorityResult.builder().uploadedDocuments(List.of(document)).build();
    when(queryGateway.query(
            any(FindPriorAuthorityByPriorAuthorityIdQuery.class), eq(PriorAuthorityResult.class)))
        .thenReturn(CompletableFuture.completedFuture(result));

    assertThat(useCase.getDocument(priorAuthorityId, documentId)).isSameAs(document);
  }

  @Test
  void givenDocumentOwnedByAnotherPriorAuthority_whenRetrieved_thenThrowsNotFound() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityResult result =
        PriorAuthorityResult.builder().uploadedDocuments(List.of()).build();
    when(queryGateway.query(
            any(FindPriorAuthorityByPriorAuthorityIdQuery.class), eq(PriorAuthorityResult.class)))
        .thenReturn(CompletableFuture.completedFuture(result));

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.getDocument(priorAuthorityId, documentId))
        .withMessage(
            "No document found with ID: %s for prior authority: %s", documentId, priorAuthorityId);
  }

  @Test
  void givenOwnedDocument_whenDownloaded_thenRetrievesItsContentFromSds() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityDocument document =
        new PriorAuthorityDocument(
            documentId,
            null,
            "evidence.pdf",
            "PDF",
            "application/pdf",
            123L,
            Instant.now(),
            "civil-apply",
            null);
    PriorAuthorityResult result =
        PriorAuthorityResult.builder().uploadedDocuments(List.of(document)).build();
    Resource resource = org.mockito.Mockito.mock(Resource.class);
    when(queryGateway.query(
            any(FindPriorAuthorityByPriorAuthorityIdQuery.class), eq(PriorAuthorityResult.class)))
        .thenReturn(CompletableFuture.completedFuture(result));
    when(sdsService.getPriorAuthorityFile(priorAuthorityId, documentId, document.fileName()))
        .thenReturn(resource);

    PriorAuthorityDocumentDownload download =
        useCase.downloadDocument(priorAuthorityId, documentId);

    assertThat(download.document()).isSameAs(document);
    assertThat(download.resource()).isSameAs(resource);
    verify(sdsService).getPriorAuthorityFile(priorAuthorityId, documentId, document.fileName());
  }
}

package uk.gov.justice.laa.dstew.access.query.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
import uk.gov.justice.laa.dstew.access.usecase.application.ApplicationQueryUseCase;

@ExtendWith(MockitoExtension.class)
class DownloadApplicationDocumentUseCaseTest {

  @Mock private ApplicationQueryUseCase applicationQueryUseCase;
  @Mock private SdsService sdsService;

  @InjectMocks private DownloadApplicationDocumentUseCase useCase;

  @Test
  void givenExistingApplication_whenDocumentDownloaded_thenRetrievesUuidKeyedContentFromSds() {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Resource resource = mock(Resource.class);
    when(sdsService.getEvidenceFile(applicationId, documentId, documentId.toString()))
        .thenReturn(resource);

    ApplicationDocumentDownload download = useCase.downloadDocument(applicationId, documentId);

    assertThat(download.fileName()).isEqualTo(documentId.toString());
    assertThat(download.resource()).isSameAs(resource);
    verify(applicationQueryUseCase).getApplicationById(applicationId);
    verify(sdsService).getEvidenceFile(applicationId, documentId, documentId.toString());
  }

  @Test
  void givenMissingApplication_whenDocumentDownloaded_thenDoesNotCallSds() {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(applicationQueryUseCase.getApplicationById(applicationId))
        .thenThrow(new ResourceNotFoundException("No application found"));

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.downloadDocument(applicationId, documentId));

    verify(sdsService, never()).getEvidenceFile(applicationId, documentId, documentId.toString());
  }
}

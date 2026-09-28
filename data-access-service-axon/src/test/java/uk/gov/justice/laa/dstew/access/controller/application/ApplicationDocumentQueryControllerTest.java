package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationDocumentDownload;
import uk.gov.justice.laa.dstew.access.query.application.DownloadApplicationDocumentUseCase;

@ExtendWith(MockitoExtension.class)
class ApplicationDocumentQueryControllerTest {

  @Mock private DownloadApplicationDocumentUseCase downloadApplicationDocumentUseCase;

  @InjectMocks private ApplicationQueryController controller;

  @Test
  void givenDocumentDownload_whenRequested_thenStreamsUuidNamedAttachment() {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Resource resource = mock(Resource.class);
    when(downloadApplicationDocumentUseCase.downloadDocument(applicationId, documentId))
        .thenReturn(new ApplicationDocumentDownload(documentId.toString(), null, resource));

    ResponseEntity<Resource> response =
        controller.downloadApplicationDocument(null, applicationId, documentId);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isSameAs(resource);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    assertThat(response.getHeaders().getFirst("Content-Disposition"))
        .contains("attachment")
        .contains(documentId.toString());
    verify(downloadApplicationDocumentUseCase).downloadDocument(applicationId, documentId);
  }
}

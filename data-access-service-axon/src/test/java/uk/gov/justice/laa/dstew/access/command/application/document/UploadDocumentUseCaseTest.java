package uk.gov.justice.laa.dstew.access.command.application.document;

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
import org.springframework.mock.web.MockMultipartFile;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

@ExtendWith(MockitoExtension.class)
class UploadDocumentUseCaseTest {

  @Mock private SdsService sdsService;

  @InjectMocks private UploadDocumentUseCase uploadDocumentUseCase;

  @Test
  void givenValidFileAndApplicationId_whenExecute_thenDelegatesToSdsServiceAndReturnsResponse() {
    UUID applicationId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "test-file.pdf", "application/pdf", "test content".getBytes());
    DocumentUploadResponse expectedResponse = mock(DocumentUploadResponse.class);
    when(sdsService.saveFile(applicationId, file)).thenReturn(expectedResponse);

    DocumentUploadResponse actualResponse = uploadDocumentUseCase.execute(applicationId, file);

    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(sdsService).saveFile(applicationId, file);
  }
}

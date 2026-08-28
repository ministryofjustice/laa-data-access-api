package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import uk.gov.justice.laa.dstew.access.command.application.document.UploadDocumentUseCase;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;

/** Verifies that each document controller endpoint delegates to the appropriate use case. */
class DocumentCommandControllerTest {

  private UploadDocumentUseCase uploadDocumentUseCase;
  private DocumentCommandController controller;

  @BeforeEach
  void setUp() {
    uploadDocumentUseCase = mock(UploadDocumentUseCase.class);
    controller = new DocumentCommandController(uploadDocumentUseCase);
  }

  @Test
  void givenValidFile_whenUploadDocument_thenDelegatesToUseCaseAndReturns201() {
    UUID id = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());
    DocumentUploadResponse expected = mock(DocumentUploadResponse.class);
    when(uploadDocumentUseCase.execute(id, file)).thenReturn(expected);

    ResponseEntity<DocumentUploadResponse> response = controller.uploadDocument(null, id, file);

    verify(uploadDocumentUseCase).execute(id, file);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isEqualTo(expected);
  }
}

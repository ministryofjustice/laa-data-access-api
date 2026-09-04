package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.UploadPriorAuthorityDocumentUseCase;
import uk.gov.justice.laa.dstew.access.model.UploadPriorAuthorityDocumentResponse;

/** Verifies that the controller delegates uploads to the use case. */
@ExtendWith(MockitoExtension.class)
class PriorAuthorityDocumentCommandControllerTest {

  @Mock private UploadPriorAuthorityDocumentUseCase uploadUseCase;

  @InjectMocks private PriorAuthorityDocumentCommandController controller;

  @Test
  void givenFile_whenUploadPriorAuthorityDocument_thenDelegatesToUseCaseAndReturnsCreated() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "content".getBytes());
    UploadPriorAuthorityDocumentResponse expectedResponse =
        new UploadPriorAuthorityDocumentResponse(documentId);
    when(uploadUseCase.execute(priorAuthorityId, file)).thenReturn(expectedResponse);

    ResponseEntity<UploadPriorAuthorityDocumentResponse> response =
        controller.uploadPriorAuthorityDocument(null, priorAuthorityId, file);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isEqualTo(expectedResponse);
    verify(uploadUseCase).execute(priorAuthorityId, file);
  }
}

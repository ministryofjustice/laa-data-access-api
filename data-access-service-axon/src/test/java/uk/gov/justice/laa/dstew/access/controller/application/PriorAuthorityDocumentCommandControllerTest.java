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

@ExtendWith(MockitoExtension.class)
class PriorAuthorityDocumentCommandControllerTest {

  @Mock private UploadPriorAuthorityDocumentUseCase uploadUseCase;

  @InjectMocks private PriorAuthorityDocumentCommandController controller;

  @Test
  void givenRequest_whenUploadPriorAuthorityDocument_thenReturnsCreatedAndBody() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "content".getBytes());
    UploadPriorAuthorityDocumentResponse useCaseResponse =
        new UploadPriorAuthorityDocumentResponse().documentId(documentId);
    when(uploadUseCase.execute(priorAuthorityId, file)).thenReturn(useCaseResponse);

    ResponseEntity<UploadPriorAuthorityDocumentResponse> response =
        controller.uploadPriorAuthorityDocument(null, priorAuthorityId, file);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getDocumentId()).isEqualTo(documentId);
    verify(uploadUseCase).execute(priorAuthorityId, file);
  }
}

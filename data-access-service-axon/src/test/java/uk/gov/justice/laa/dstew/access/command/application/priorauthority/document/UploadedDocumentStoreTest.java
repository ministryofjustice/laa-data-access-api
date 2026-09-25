package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UploadedDocumentStoreTest {

  @Mock private UploadedDocumentRepository repository;

  @InjectMocks private UploadedDocumentStore store;

  @Test
  void givenFilenameMapping_whenSaved_thenBlindlyUpsertsIt() {
    UUID submissionId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();

    store.save(submissionId, documentId, "evidence.pdf");

    verify(repository).upsert(documentId, submissionId, "evidence.pdf");
  }
}

package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Persistence interface for uploaded documents metadata. */
public interface UploadedDocumentRepository extends JpaRepository<UploadedDocument, UUID> {

  @Modifying
  @Transactional
  @Query(
      value =
          """
          INSERT INTO axon.uploaded_documents (document_id, submission_id, original_filename)
          VALUES (:documentId, :submissionId, :originalFilename)
          ON CONFLICT (document_id) DO UPDATE
          SET submission_id = EXCLUDED.submission_id,
              original_filename = EXCLUDED.original_filename
          """,
      nativeQuery = true)
  void upsert(
      @Param("documentId") UUID documentId,
      @Param("submissionId") UUID submissionId,
      @Param("originalFilename") String originalFilename);
}

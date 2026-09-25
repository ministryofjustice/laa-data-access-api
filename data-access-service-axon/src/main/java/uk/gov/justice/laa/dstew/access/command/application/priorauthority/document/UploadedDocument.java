package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/** Query-side metadata for a file uploaded to a prior-authority submission. */
@Entity
@Table(name = "uploaded_documents")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadedDocument {

  @Id
  @Column(name = "document_id")
  private UUID documentId;

  @Column(name = "submission_id", nullable = false)
  private UUID submissionId;

  @Column(name = "original_filename", nullable = false)
  @NonNull
  private String originalFilename;
}

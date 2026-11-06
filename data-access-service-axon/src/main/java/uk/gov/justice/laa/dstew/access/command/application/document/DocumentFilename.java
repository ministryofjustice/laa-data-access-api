package uk.gov.justice.laa.dstew.access.command.application.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Filename for a document, owned by an application or prior authority. */
@Entity
@Table(name = "document_filename")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentFilename {

  @Id
  @Column(name = "document_id")
  private UUID documentId;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "filename", nullable = false)
  private String filename;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  /** Marks the filename as deleted without removing the row. */
  public void markDeleted(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }
}

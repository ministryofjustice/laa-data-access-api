package uk.gov.justice.laa.dstew.access.command.application.data;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Mutable draft content for an Application that has not yet been submitted. */
@Entity
@Table(name = "application_draft")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDraft {

  @Id
  @Column(name = "application_id")
  private UUID applicationId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private ApplicationDraftPayload payload;

  @Column(name = "payload_hash", nullable = false)
  private String payloadHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}

package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Mutable draft content for a Prior Authority submission that has not yet been submitted. */
@Entity
@Table(name = "prior_authority_draft")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriorAuthorityDraft {

  @Id
  @Column(name = "prior_authority_id")
  private UUID priorAuthorityId;

  @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private PriorAuthorityDataPayload payload;

  @Column(name = "payload_hash", nullable = false)
  private String payloadHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  /** Updates the mutable draft fields while preserving creation metadata. */
  public void update(PriorAuthorityDataPayload payload, String payloadHash, Instant updatedAt) {
    this.payload = payload;
    this.payloadHash = payloadHash;
    this.updatedAt = updatedAt;
  }
}

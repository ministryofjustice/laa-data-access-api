package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Mutable draft content for a Prior Authority submission that has not yet been submitted. */
@Entity
@Table(name = "prior_authority_draft")
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

  protected PriorAuthorityDraft() {
    // JPA constructor
  }

  /**
   * Creates a draft persistence entity.
   *
   * @param priorAuthorityId draft identifier
   * @param applicationId parent application identifier
   * @param payload draft payload content
   * @param payloadHash fingerprint of the serialised request
   * @param createdAt initial create timestamp
   * @param updatedAt latest update timestamp
   */
  public PriorAuthorityDraft(
      UUID priorAuthorityId,
      UUID applicationId,
      PriorAuthorityDataPayload payload,
      String payloadHash,
      Instant createdAt,
      Instant updatedAt) {
    this.priorAuthorityId = priorAuthorityId;
    this.applicationId = applicationId;
    this.payload = payload;
    this.payloadHash = payloadHash;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID getPriorAuthorityId() {
    return priorAuthorityId;
  }

  public UUID getApplicationId() {
    return applicationId;
  }

  public PriorAuthorityDataPayload getPayload() {
    return payload;
  }

  public String getPayloadHash() {
    return payloadHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

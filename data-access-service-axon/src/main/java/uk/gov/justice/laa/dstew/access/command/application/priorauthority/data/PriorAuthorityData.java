package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only sensitive data associated with one version of a Prior Authority submission. */
@Entity
@Table(name = "prior_authority_data")
public class PriorAuthorityData {

  @EmbeddedId private PriorAuthorityDataId id;

  @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private PriorAuthorityDataPayload payload;

  @Column(name = "payload_hash", nullable = false)
  private String payloadHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected PriorAuthorityData() {
    // JPA constructor
  }

  /**
   * Creates an immutable prior-authority data persistence entity for a specific version.
   *
   * @param id composite identifier containing submission id and data version
   * @param applicationId parent application identifier
   * @param payload versioned payload content
   * @param payloadHash fingerprint of the serialised request
   * @param createdAt creation timestamp for this immutable version
   */
  public PriorAuthorityData(
      PriorAuthorityDataId id,
      UUID applicationId,
      PriorAuthorityDataPayload payload,
      String payloadHash,
      Instant createdAt) {
    this.id = id;
    this.applicationId = applicationId;
    this.payload = payload;
    this.payloadHash = payloadHash;
    this.createdAt = createdAt;
  }

  public PriorAuthorityDataId getId() {
    return id;
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
}

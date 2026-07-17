package uk.gov.justice.laa.dstew.access.query.draft;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Mutable draft store. Structurally identical to the submissions store, but whereas a submission is
 * an immutable record of what was submitted, a draft is expected to be updated in place as it is
 * edited before submission.
 */
@Entity
@Table(name = "drafts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftRecord {

  @Id
  @Column(name = "event_id")
  private UUID eventId;

  @Column(name = "apply_application_id")
  private UUID applyApplicationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "draft_type")
  private DraftType draftType;

  @Column(name = "causation_id")
  private UUID causationId;

  @Column(name = "correlation_id")
  private UUID correlationId;

  @JdbcTypeCode(SqlTypes.JSON)
  private DraftData data;

  @Column(name = "created_at")
  private Instant createdAt;
}

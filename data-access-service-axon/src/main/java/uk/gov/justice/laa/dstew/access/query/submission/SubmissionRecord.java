package uk.gov.justice.laa.dstew.access.query.submission;

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
 * Payload store for a submitted application. Holds the raw submission as a JSON blob keyed by the
 * minted {@code content_id} that the pointer event references. Written by the application layer
 * before the event is emitted, and deletable independently of the event stream. It is the
 * system-of-record for the submitted body and is NOT rebuildable by replaying events.
 */
@Entity
@Table(name = "submissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionRecord {

  @Id
  @Column(name = "content_id")
  private UUID contentId;

  @Column(name = "apply_application_id")
  private UUID applyApplicationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "submission_type")
  private SubmissionType submissionType;

  @Column(name = "causation_id")
  private UUID causationId;

  @Column(name = "correlation_id")
  private UUID correlationId;

  @JdbcTypeCode(SqlTypes.JSON)
  private SubmissionData data;

  @Column(name = "created_at")
  private Instant createdAt;
}

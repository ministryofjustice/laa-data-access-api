package uk.gov.justice.laa.dstew.access.query.draft;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Deletable, mutable store for an application draft body. Holds the raw, unvalidated draft payload
 * as a JSON blob keyed by the application id, written by the application layer and overwritten in
 * place as the draft is edited. On submit the body is validated and sealed into an immutable {@code
 * submissions} row. It is the system-of-record for the draft body and is NOT rebuildable by
 * replaying events (the draft events are PII-free pointers).
 */
@Entity
@Table(name = "drafts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftRecord {

  @Id
  @Column(name = "apply_application_id")
  private UUID applyApplicationId;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> content;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;
}

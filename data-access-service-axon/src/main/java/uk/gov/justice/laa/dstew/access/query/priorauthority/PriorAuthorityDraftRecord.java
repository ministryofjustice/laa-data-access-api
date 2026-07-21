package uk.gov.justice.laa.dstew.access.query.priorauthority;

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
 * Deletable, mutable store for a prior authority draft body. Holds the raw draft content as a JSON
 * blob keyed by the {@code prior_authority_id} that the pointer events reference. Written by the
 * application layer before the event is emitted, overwritten in place on update, and deletable
 * independently of the event stream. It is the system-of-record for the draft body and is NOT
 * rebuildable by replaying events.
 */
@Entity
@Table(name = "prior_authority_drafts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriorAuthorityDraftRecord {

  @Id
  @Column(name = "prior_authority_id")
  private UUID priorAuthorityId;

  @Column(name = "apply_application_id")
  private UUID applyApplicationId;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> content;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;
}

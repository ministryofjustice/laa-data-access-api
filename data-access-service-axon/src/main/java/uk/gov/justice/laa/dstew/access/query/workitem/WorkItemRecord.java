package uk.gov.justice.laa.dstew.access.query.workitem;

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

/**
 * Read-model row for a single caseworker work item. A pure projection of submission and assignment
 * events — never written by the command side. Its identity ({@code work_item_id}) is the natural id
 * of the underlying thing: the application id for an {@code APPLICATION} item, the prior authority
 * id for a {@code PRIOR_AUTHORITY} item, so the polymorphic assign endpoint can resolve it back to
 * the owning aggregate.
 */
@Entity
@Table(name = "work_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkItemRecord {

  @Id
  @Column(name = "work_item_id")
  private UUID workItemId;

  @Enumerated(EnumType.STRING)
  @Column(name = "work_type")
  private WorkType workType;

  @Column(name = "application_id")
  private UUID applicationId;

  @Column(name = "prior_authority_id")
  private UUID priorAuthorityId;

  @Column(name = "laa_reference")
  private String laaReference;

  @Column(name = "assigned_caseworker_id")
  private UUID assignedCaseworkerId;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;
}

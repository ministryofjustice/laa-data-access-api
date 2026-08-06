package uk.gov.justice.laa.dstew.access.query.workqueue;

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

/** Replayable current-state read model for a work queue item. */
@Entity
@Table(name = "work_queue_current_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkQueueReadModel {

  @Id
  @Column(name = "item_id")
  private UUID itemId;

  @Column(name = "item_type", nullable = false)
  @Enumerated(EnumType.STRING)
  private WorkQueueItemType itemType;

  @Column(name = "assigned_to")
  private UUID assignedTo;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "laa_reference")
  private String laaReference;
}

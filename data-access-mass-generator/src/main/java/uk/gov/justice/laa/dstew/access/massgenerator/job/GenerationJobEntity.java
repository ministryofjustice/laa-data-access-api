package uk.gov.justice.laa.dstew.access.massgenerator.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "generation_jobs")
public class GenerationJobEntity {

  @Id private String id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private JobStatus status;

  @Column(name = "target_count", nullable = false)
  private int targetCount;

  @Column(name = "processed_count", nullable = false)
  private int processedCount;

  @Column(name = "decided_count", nullable = false)
  private int decidedCount;

  @Column(name = "error_count", nullable = false)
  private int errorCount;

  @Column(name = "cleanup_requested", nullable = false)
  private boolean cleanupRequested;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "error_message")
  private String errorMessage;

  @Column private Double throughput;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}

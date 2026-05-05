package uk.gov.justice.laa.dstew.access.massgenerator.job;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationJob {
  private String jobId;
  private JobStatus status;
  private int targetCount;
  private int processedCount;
  private int decidedCount;
  private Instant startedAt;
  private Instant completedAt;
  private String errorMessage;
  private Double throughput;
  private boolean cleanupRequested;
  private int errorCount;
}

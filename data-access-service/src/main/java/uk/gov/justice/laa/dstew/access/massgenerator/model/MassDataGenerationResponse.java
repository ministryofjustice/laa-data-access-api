package uk.gov.justice.laa.dstew.access.massgenerator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response model containing statistics from mass data generation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MassDataGenerationResponse {

  /** Total number of application records generated. */
  private int recordsGenerated;

  /** Number of applications that received decisions. */
  private int decidedCount;

  /** Number of linked application pairs created. */
  private int linkedCount;

  /** Total time taken in milliseconds. */
  private long durationMillis;

  /** Average throughput in records per second. */
  private double throughputRecordsPerSecond;
}

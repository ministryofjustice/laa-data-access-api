package uk.gov.justice.laa.dstew.access.massgenerator.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for mass data generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MassDataGenerationRequest {

  /**
   * Number of application records to generate.
   */
  @Positive
  @Builder.Default
  private int count = 100;

  /**
   * Number of records to process before flushing the session.
   */
  @Positive
  @Builder.Default
  private int batchSize = 500;

  /**
   * Percentage (0.0 to 1.0) of applications that should have decisions.
   */
  @Min(0)
  @Max(1)
  @Builder.Default
  private double decisionRate = 0.4;

  /**
   * Percentage (0.0 to 1.0) of applications that should be linked together.
   */
  @Min(0)
  @Max(1)
  @Builder.Default
  private double linkRate = 0.3;
}


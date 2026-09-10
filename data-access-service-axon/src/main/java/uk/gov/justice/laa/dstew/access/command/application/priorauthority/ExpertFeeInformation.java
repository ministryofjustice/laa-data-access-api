package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Represents the fee information if an expert is used. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertFeeInformation {
  private Double newFixedRateAmount;
  private Double newHourlyRateAmount;
}

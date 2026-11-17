package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.content.priorauthority.TimeRequested;

/** Represents the fee information if an expert is used. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertFeeInformation {
  private BigDecimal grantedFixedRateAmount;
  private BigDecimal grantedHourlyRateAmount;
  private TimeRequested grantedTime;
}

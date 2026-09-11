package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Represents the information if an apportionment is due. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApportionmentInformation {
  private Double newClientShareAmount;
}

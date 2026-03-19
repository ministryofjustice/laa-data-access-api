package uk.gov.justice.laa.dstew.access.utils.generator.proceeding;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class ProceedingGenerator extends BaseGenerator<Proceeding, Proceeding.ProceedingBuilder> {
  public ProceedingGenerator() {
    super(Proceeding::toBuilder, Proceeding.ProceedingBuilder::build);
  }

  @Override
  public Proceeding createDefault() {
    return Proceeding.builder()
        .id(UUID.randomUUID())
        .categoryOfLaw("Family")
        .matterType("SPECIAL_CHILDREN_ACT")
        .leadProceeding(true)
        .usedDelegatedFunctions(true)
        .description("Proceeding description")
        .meaning("hearing")
        .usedDelegatedFunctionsOn(LocalDate.parse("2025-05-06"))
        .substantiveCostLimitation("23.45")
        .substantiveLevelOfServiceName("service")
            .scopeLimitations(
              List.of(
                Map.of(
                  "id", "100",
                  "code", "AB123D",
                  "meaning", "hearing"
                )
              )
            )
        .build();
  }
}
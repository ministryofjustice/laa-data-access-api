package uk.gov.justice.laa.dstew.access.utils.generator.proceeding;

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
        .build();
  }
}


package uk.gov.justice.laa.dstew.access.utils.generator.getapplication;

import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.dto.ProceedingDbProjection;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link ProceedingDbProjection} test data. */
public class ProceedingDbProjectionGenerator
    extends BaseGenerator<
        ProceedingDbProjection, ProceedingDbProjection.ProceedingDbProjectionBuilder> {

  /** Constructs the generator. */
  public ProceedingDbProjectionGenerator() {
    super(
        ProceedingDbProjection::toBuilder,
        ProceedingDbProjection.ProceedingDbProjectionBuilder::build);
  }

  @Override
  public ProceedingDbProjection createDefault() {
    return ProceedingDbProjection.builder()
        .proceedingId(UUID.randomUUID())
        .description("Test proceeding")
        .meritsDecision("GRANTED")
        .proceedingType("hearing")
        .categoryOfLaw("Family")
        .matterType("SPECIAL_CHILDREN_ACT")
        .levelOfService("Full representation")
        .substantiveCostLimitation(1350.0)
        .delegatedFunctionsDate(LocalDate.of(2025, 5, 6))
        .scopeLimitations(Collections.emptyList())
        .involvedChildren(Collections.emptyList())
        .build();
  }
}

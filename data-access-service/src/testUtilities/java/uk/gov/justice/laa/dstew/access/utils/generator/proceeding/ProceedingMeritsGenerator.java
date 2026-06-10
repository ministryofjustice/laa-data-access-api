package uk.gov.justice.laa.dstew.access.utils.generator.proceeding;

import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.ProceedingLinkedChild;
import uk.gov.justice.laa.dstew.access.model.ProceedingMerits;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationMeritsGenerator;

public class ProceedingMeritsGenerator
    extends BaseGenerator<ProceedingMerits, ProceedingMerits.ProceedingMeritsBuilder> {

  public ProceedingMeritsGenerator() {
    super(ProceedingMerits::toBuilder, ProceedingMerits.ProceedingMeritsBuilder::build);
  }

  @Override
  public ProceedingMerits createDefault() {
    return ProceedingMerits.builder()
        .proceedingId(UUID.randomUUID())
        .proceedingLinkedChildren(
            List.of(
                ProceedingLinkedChild.builder()
                    .involvedChildId(ApplicationMeritsGenerator.DEFAULT_INVOLVED_CHILD_ID)
                    .build()))
        .build();
  }
}

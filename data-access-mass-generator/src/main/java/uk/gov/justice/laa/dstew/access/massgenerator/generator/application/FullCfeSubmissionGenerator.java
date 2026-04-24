package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.massgenerator.model.FullCfeSubmission;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullCfeSubmissionGenerator
    extends BaseGenerator<FullCfeSubmission, FullCfeSubmission.FullCfeSubmissionBuilder> {

  public FullCfeSubmissionGenerator() {
    super(FullCfeSubmission::toBuilder, FullCfeSubmission.FullCfeSubmissionBuilder::build);
  }

  private String randomInstant() {
    return Instant.now().minus(faker.number().numberBetween(0, 365), ChronoUnit.DAYS).toString();
  }

  @Override
  public FullCfeSubmission createDefault() {
    return FullCfeSubmission.builder()
        .id(UUID.randomUUID().toString())
        .legalAidApplicationId(UUID.randomUUID().toString())
        .assessmentId(null)
        .aasmState(faker.options().option("cfe_not_called", "complete", "failed"))
        .errorMessage(null)
        .cfeResult(null)
        .createdAt(randomInstant())
        .updatedAt(randomInstant())
        .build();
  }
}

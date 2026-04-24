package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.time.Instant;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class ApplicationSummaryDtoGenerator
    extends BaseGenerator<
        ApplicationSummaryDto, ApplicationSummaryDto.ApplicationSummaryDtoBuilder> {

  public ApplicationSummaryDtoGenerator() {
    super(
        ApplicationSummaryDto::toBuilder,
        ApplicationSummaryDto.ApplicationSummaryDtoBuilder::build);
  }

  @Override
  public ApplicationSummaryDto createDefault() {
    return ApplicationSummaryDto.builder()
        .id(UUID.randomUUID())
        .laaReference("REF7327")
        .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
        .createdAt(Instant.now())
        .modifiedAt(Instant.now())
        .caseworkerId(UUID.randomUUID())
        .build();
  }

  @Override
  public ApplicationSummaryDto createRandom() {
    return createDefault().toBuilder().laaReference(faker.bothify("REF####")).build();
  }
}

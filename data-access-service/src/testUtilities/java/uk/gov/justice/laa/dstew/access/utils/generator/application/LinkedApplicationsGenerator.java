package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.LinkedApplication;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingGenerator;

public class LinkedApplicationsGenerator extends BaseGenerator<LinkedApplication, LinkedApplication.LinkedApplicationBuilder> {
  private final ProceedingGenerator proceedingDtoGenerator = new ProceedingGenerator();

  public LinkedApplicationsGenerator() {
    super(LinkedApplication::toBuilder, LinkedApplication.LinkedApplicationBuilder::build);
  }

  @Override
  public LinkedApplication createDefault() {
    UUID applicationId = UUID.randomUUID();
    UUID associatedApplicationId = UUID.randomUUID();
    return LinkedApplication.builder()
        .leadApplicationId(applicationId)
        .associatedApplicationId(associatedApplicationId).build();
  }
}

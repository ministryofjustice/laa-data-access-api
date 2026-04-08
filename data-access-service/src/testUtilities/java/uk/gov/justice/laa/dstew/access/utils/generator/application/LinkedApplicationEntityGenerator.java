package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.entity.LinkedApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.LinkedApplication;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingGenerator;

public class LinkedApplicationEntityGenerator extends BaseGenerator<LinkedApplicationEntity, LinkedApplicationEntity.LinkedApplicationEntityBuilder> {

  public LinkedApplicationEntityGenerator() {
    super(LinkedApplicationEntity::toBuilder, LinkedApplicationEntity.LinkedApplicationEntityBuilder::build);
  }

  @Override
  public LinkedApplicationEntity createDefault() {
    UUID applicationId = UUID.randomUUID();
    UUID associatedApplicationId = UUID.randomUUID();
    return LinkedApplicationEntity.builder()
        .leadApplicationId(applicationId)
        .associatedApplicationId(associatedApplicationId).build();
  }
}

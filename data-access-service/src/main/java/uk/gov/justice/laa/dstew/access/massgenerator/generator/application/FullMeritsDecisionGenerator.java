package uk.gov.justice.laa.dstew.access.massgenerator.generator.application;

import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class FullMeritsDecisionGenerator
    extends BaseGenerator<MeritsDecisionEntity, MeritsDecisionEntity.MeritsDecisionEntityBuilder> {

  public FullMeritsDecisionGenerator() {
    super(MeritsDecisionEntity::toBuilder, MeritsDecisionEntity.MeritsDecisionEntityBuilder::build);
  }

  @Override
  public MeritsDecisionEntity createDefault() {
    return MeritsDecisionEntity.builder()
        .decision(
            faker.options().option(MeritsDecisionStatus.GRANTED, MeritsDecisionStatus.REFUSED))
        .justification(faker.lorem().sentence())
        .reason(faker.lorem().sentence())
        .build();
  }
}

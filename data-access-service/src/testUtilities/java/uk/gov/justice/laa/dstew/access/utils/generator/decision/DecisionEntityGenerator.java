package uk.gov.justice.laa.dstew.access.utils.generator.decision;

import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class DecisionEntityGenerator extends BaseGenerator<DecisionEntity, DecisionEntity.DecisionEntityBuilder> {
    public DecisionEntityGenerator() {
        super(DecisionEntity::toBuilder, DecisionEntity.DecisionEntityBuilder::build);
    }

    @Override
    public DecisionEntity createDefault() {
        return DecisionEntity.builder()
                .build();
    }
}


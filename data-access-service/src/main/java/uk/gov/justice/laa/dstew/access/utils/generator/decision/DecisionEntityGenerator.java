package uk.gov.justice.laa.dstew.access.utils.generator.decision;

import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionsEntityGenerator;

import java.util.HashSet;
import java.util.Set;

public class DecisionEntityGenerator extends BaseGenerator<DecisionEntity, DecisionEntity.DecisionEntityBuilder> {

    public DecisionEntityGenerator() {
        super(DecisionEntity::toBuilder, DecisionEntity.DecisionEntityBuilder::build);
    }

    @Override
    public DecisionEntity createDefault() {
        return DecisionEntity.builder()
                .overallDecision(DecisionStatus.REFUSED)
                .build();
    }
}


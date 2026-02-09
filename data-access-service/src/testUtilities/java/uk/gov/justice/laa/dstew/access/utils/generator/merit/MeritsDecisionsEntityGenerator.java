package uk.gov.justice.laa.dstew.access.utils.generator.merit;

import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import java.util.UUID;

public class MeritsDecisionsEntityGenerator extends BaseGenerator<MeritsDecisionEntity, MeritsDecisionEntity.MeritsDecisionEntityBuilder> {
    public MeritsDecisionsEntityGenerator() {
        super(MeritsDecisionEntity::toBuilder, MeritsDecisionEntity.MeritsDecisionEntityBuilder::build);
    }

    @Override
    public MeritsDecisionEntity createDefault() {
        return MeritsDecisionEntity.builder()
                .build();
    }
}


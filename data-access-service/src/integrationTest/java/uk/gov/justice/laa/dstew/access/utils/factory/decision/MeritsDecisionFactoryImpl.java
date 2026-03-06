package uk.gov.justice.laa.dstew.access.utils.factory.decision;

import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

import java.util.function.Consumer;

public class MeritsDecisionFactoryImpl implements Factory<MeritsDecisionEntity, MeritsDecisionEntity.MeritsDecisionEntityBuilder> {

    @Override
    public MeritsDecisionEntity create() {
        return MeritsDecisionEntity.builder()
                .build();
    }

    public MeritsDecisionEntity create(Consumer<MeritsDecisionEntity.MeritsDecisionEntityBuilder> customiser) {
        MeritsDecisionEntity entity = create();
        MeritsDecisionEntity.MeritsDecisionEntityBuilder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }

}
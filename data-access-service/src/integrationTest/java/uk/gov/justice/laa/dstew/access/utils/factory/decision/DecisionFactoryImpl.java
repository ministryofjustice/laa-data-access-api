package uk.gov.justice.laa.dstew.access.utils.factory.decision;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;
import java.util.function.Consumer;

public class DecisionFactoryImpl implements Factory<DecisionEntity, DecisionEntity.DecisionEntityBuilder> {

    @Override
    public DecisionEntity create() {
        return DecisionEntity.builder()
                .build();
    }

    public DecisionEntity create(Consumer<DecisionEntity.DecisionEntityBuilder> customiser) {
        DecisionEntity entity = create();
        DecisionEntity.DecisionEntityBuilder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }

}

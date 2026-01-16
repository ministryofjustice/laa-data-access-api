package uk.gov.justice.laa.dstew.access.utils.factory.proceeding;

import uk.gov.justice.laa.dstew.access.entity.ProceedingsEntity;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class ProceedingFactoryImpl implements Factory<ProceedingsEntity, ProceedingsEntity.ProceedingsEntityBuilder> {

    @Override
    public ProceedingsEntity create() {
        return ProceedingsEntity.builder()
                .applyProceedingId(UUID.randomUUID())
                .description("description")
                .proceedingContent(new HashMap<>(Map.of(
                        "test", "content"
                )))
                .build();
    }

    public ProceedingsEntity create(Consumer<ProceedingsEntity.ProceedingsEntityBuilder> customiser) {
        ProceedingsEntity entity = create();
        ProceedingsEntity.ProceedingsEntityBuilder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }

}

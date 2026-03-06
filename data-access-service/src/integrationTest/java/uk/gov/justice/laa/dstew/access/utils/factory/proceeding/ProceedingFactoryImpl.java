package uk.gov.justice.laa.dstew.access.utils.factory.proceeding;

import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class ProceedingFactoryImpl implements Factory<ProceedingEntity, ProceedingEntity.ProceedingEntityBuilder> {

    @Override
    public ProceedingEntity create() {
        return ProceedingEntity.builder()
                .applyProceedingId(UUID.randomUUID())
                .description("description")
                .proceedingContent(new HashMap<>(Map.of(
                        "test", "content"
                )))
                .build();
    }

    public ProceedingEntity create(Consumer<ProceedingEntity.ProceedingEntityBuilder> customiser) {
        ProceedingEntity entity = create();
        ProceedingEntity.ProceedingEntityBuilder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }

}

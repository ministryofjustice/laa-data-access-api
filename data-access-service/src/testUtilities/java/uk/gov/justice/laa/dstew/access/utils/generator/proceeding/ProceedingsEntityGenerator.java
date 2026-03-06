package uk.gov.justice.laa.dstew.access.utils.generator.proceeding;

import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProceedingsEntityGenerator extends BaseGenerator<ProceedingEntity, ProceedingEntity.ProceedingEntityBuilder> {
    public ProceedingsEntityGenerator() {
        super(ProceedingEntity::toBuilder, ProceedingEntity.ProceedingEntityBuilder::build);
    }

    @Override
    public ProceedingEntity createDefault() {
        return ProceedingEntity.builder()
                .applyProceedingId(UUID.randomUUID())
                .description("description")
                .proceedingContent(new HashMap<>(Map.of(
                        "test", "content"
                )))
                .build();
    }
}


package uk.gov.justice.laa.dstew.access.utils.factory.proceeding;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

import java.util.UUID;

@Profile("unit-test")
@Component
public class ProceedingsEntityFactory extends BaseFactory<ProceedingEntity, ProceedingEntity.ProceedingEntityBuilder> {

    public ProceedingsEntityFactory() {
        super(ProceedingEntity::toBuilder, ProceedingEntity.ProceedingEntityBuilder::build);
    }

    @Override
    public ProceedingEntity createDefault() {
        return ProceedingEntity.builder()
                .id(UUID.randomUUID())
                .build();
    }
}
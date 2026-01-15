package uk.gov.justice.laa.dstew.access.utils.factory.proceeding;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ProceedingsEntity;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

import java.util.UUID;

@Profile("unit-test")
@Component
public class ProceedingsEntityFactory extends BaseFactory<ProceedingsEntity, ProceedingsEntity.ProceedingsEntityBuilder> {

    public ProceedingsEntityFactory() {
        super(ProceedingsEntity::toBuilder, ProceedingsEntity.ProceedingsEntityBuilder::build);
    }

    @Override
    public ProceedingsEntity createDefault() {
        return ProceedingsEntity.builder()
                .id(UUID.randomUUID())
                .build();
    }
}
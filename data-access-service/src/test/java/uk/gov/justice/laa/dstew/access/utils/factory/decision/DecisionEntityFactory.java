package uk.gov.justice.laa.dstew.access.utils.factory.decision;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.enums.DecisionStatus;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.merit.MeritsDecisionsEntityFactory;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Profile("unit-test")
@Component
public class DecisionEntityFactory extends BaseFactory<DecisionEntity, DecisionEntity.DecisionEntityBuilder> {

    public DecisionEntityFactory() {
        super(DecisionEntity::toBuilder, DecisionEntity.DecisionEntityBuilder::build);
    }

    @Override
    public DecisionEntity createDefault() {
        return DecisionEntity.builder()
                .id(UUID.randomUUID())
                .build();
    }
}
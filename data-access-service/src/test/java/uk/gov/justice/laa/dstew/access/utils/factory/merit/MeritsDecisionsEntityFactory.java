package uk.gov.justice.laa.dstew.access.utils.factory.merit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.enums.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetails;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.refusal.RefusalDetailsFactory;

import java.time.Instant;
import java.util.UUID;

@Profile("unit-test")
@Component
public class MeritsDecisionsEntityFactory extends BaseFactory<MeritsDecisionEntity, MeritsDecisionEntity.MeritsDecisionEntityBuilder> {

    public MeritsDecisionsEntityFactory() {
        super(MeritsDecisionEntity::toBuilder, MeritsDecisionEntity.MeritsDecisionEntityBuilder::build);
    }

    @Override
    public MeritsDecisionEntity createDefault() {
        return MeritsDecisionEntity.builder()
                .id(UUID.randomUUID())
                .build();
    }
}
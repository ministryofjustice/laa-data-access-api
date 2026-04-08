package uk.gov.justice.laa.dstew.access.utils.generator.caseworker;

import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import java.time.Instant;
import java.util.UUID;

public class CaseworkerGenerator extends BaseGenerator<CaseworkerEntity, CaseworkerEntity.CaseworkerEntityBuilder> {
    public CaseworkerGenerator() {
        super(CaseworkerEntity::toBuilder, CaseworkerEntity.CaseworkerEntityBuilder::build);
    }

    @Override
    public CaseworkerEntity createDefault() {
        return CaseworkerEntity.builder()
                .username("John Doe")
                .createdAt(Instant.now())
                .modifiedAt(Instant.now())
                .build();
    }

    @Override
    public CaseworkerEntity createRandom() {
        return CaseworkerEntity.builder()
                .username(faker.name().firstName().toLowerCase() + "." + faker.name().lastName().toLowerCase())
                .createdAt(Instant.now())
                .modifiedAt(Instant.now())
                .build();
    }
}


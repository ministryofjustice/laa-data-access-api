package uk.gov.justice.laa.dstew.access.utils.generator.caseworker;

import java.time.Instant;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

public class CaseworkerGenerator extends BaseGenerator<CaseworkerEntity, CaseworkerEntity.CaseworkerEntityBuilder> {
    public CaseworkerGenerator() {
        super(CaseworkerEntity::toBuilder, CaseworkerEntity.CaseworkerEntityBuilder::build);
    }

    @Override
    public CaseworkerEntity createDefault() {
        return CaseworkerEntity.builder()
                .username("John Doe")
            .createdAt(Instant.parse("2024-01-01T12:00:00Z"))
            .modifiedAt(Instant.parse("2024-01-01T12:00:00Z"))
                .build();
    }

    @Override
    public CaseworkerEntity createRandom() {
        return CaseworkerEntity.builder()
                .username(faker.name().firstName().toLowerCase() + "." + faker.name().lastName().toLowerCase())
            .createdAt(Instant.parse("2024-01-01T12:00:00Z"))
            .modifiedAt(Instant.parse("2024-01-01T12:00:00Z"))
                .build();
    }
}


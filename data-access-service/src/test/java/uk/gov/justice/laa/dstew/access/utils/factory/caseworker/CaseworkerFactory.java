package uk.gov.justice.laa.dstew.access.utils.factory.caseworker;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

import java.time.Instant;
import java.util.UUID;

@Profile("unit-test")
@Component
public class CaseworkerFactory extends BaseFactory<CaseworkerEntity, CaseworkerEntity.CaseworkerEntityBuilder> {

    public CaseworkerFactory() {
        super(CaseworkerEntity::toBuilder, CaseworkerEntity.CaseworkerEntityBuilder::build);
    }

    @Override
    public CaseworkerEntity createDefault() {
        return CaseworkerEntity.builder()
                .id(UUID.randomUUID())
                .username("John Doe")
                .createdAt(Instant.now())
                .modifiedAt(Instant.now())
                .build();
    }

    @Override
    public CaseworkerEntity createRandom() {
        return CaseworkerEntity.builder()
                .id(UUID.randomUUID())
                .username(faker.name().firstName().toLowerCase() + "." + faker.name().lastName().toLowerCase())
                .createdAt(Instant.now())
                .modifiedAt(Instant.now())
                .build();
    }
}

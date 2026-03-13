package uk.gov.justice.laa.dstew.access.utils.factory.caseworker;

import java.time.Instant;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

import java.util.function.Consumer;
import uk.gov.justice.laa.dstew.access.utils.helpers.DateTimeHelper;

public class CaseworkerFactoryImpl implements Factory<CaseworkerEntity, CaseworkerEntity.CaseworkerEntityBuilder> {
    @Override
    public CaseworkerEntity create() {
      Instant creationTime = DateTimeHelper.GetSystemInstanceWithoutNanoseconds();
      return CaseworkerEntity.builder()
                .username("JohnDoe")
            .createdAt(creationTime)
            .modifiedAt(creationTime)
                .build();
    }

    @Override
    public CaseworkerEntity create(Consumer<CaseworkerEntity.CaseworkerEntityBuilder> customiser) {
        CaseworkerEntity entity = create();
        CaseworkerEntity.CaseworkerEntityBuilder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }
}
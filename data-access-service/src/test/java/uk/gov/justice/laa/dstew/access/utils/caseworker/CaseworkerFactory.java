package uk.gov.justice.laa.dstew.access.utils.caseworker;

import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.model.Application;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class CaseworkerFactory {

    public static CaseworkerEntity create() {
        return CaseworkerEntity.builder()
                .id(UUID.randomUUID())
                .username("John Doe")
                .createdAt(Instant.now())
                .modifiedAt(Instant.now())
                .build();
    }

    public static CaseworkerEntity create(Consumer<CaseworkerEntity.CaseworkerEntityBuilder> customiser) {
        CaseworkerEntity entity = create();
        CaseworkerEntity.CaseworkerEntityBuilder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }
}

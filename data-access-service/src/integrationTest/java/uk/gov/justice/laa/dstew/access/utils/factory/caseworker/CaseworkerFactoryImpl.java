package uk.gov.justice.laa.dstew.access.utils.factory.caseworker;

import java.util.function.Consumer;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

public class CaseworkerFactoryImpl
    implements Factory<CaseworkerEntity, CaseworkerEntity.CaseworkerEntityBuilder> {
  @Override
  public CaseworkerEntity create() {
    return CaseworkerEntity.builder().username("JohnDoe").build();
  }

  @Override
  public CaseworkerEntity create(Consumer<CaseworkerEntity.CaseworkerEntityBuilder> customiser) {
    CaseworkerEntity entity = create();
    CaseworkerEntity.CaseworkerEntityBuilder builder = entity.toBuilder();
    customiser.accept(builder);
    return builder.build();
  }
}

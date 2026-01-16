package uk.gov.justice.laa.dstew.access.utils.factory.caseworker;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

public class CaseworkerAssignFactoryImpl
    implements Factory<CaseworkerAssignRequest, CaseworkerAssignRequest.Builder> {

  @Override
  public CaseworkerAssignRequest create() {
    return CaseworkerAssignRequest.builder()
        .caseworkerId(BaseIntegrationTest.CaseworkerJohnDoe.getId())
        .applicationIds(List.of(UUID.randomUUID()))
        .build();
  }

  public CaseworkerAssignRequest create(Consumer<CaseworkerAssignRequest.Builder> customiser) {
    CaseworkerAssignRequest entity = create();
    CaseworkerAssignRequest.Builder builder = entity.toBuilder();
    customiser.accept(builder);
    return builder.build();
  }
}

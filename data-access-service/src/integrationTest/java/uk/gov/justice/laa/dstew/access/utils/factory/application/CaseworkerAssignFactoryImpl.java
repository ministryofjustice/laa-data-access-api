package uk.gov.justice.laa.dstew.access.utils.factory.application;

import uk.gov.justice.laa.dstew.access.model.CaseworkerAssignRequest;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

import java.util.function.Consumer;

public class CaseworkerAssignFactoryImpl implements Factory<CaseworkerAssignRequest, CaseworkerAssignRequest.Builder> {

    @Override
    public CaseworkerAssignRequest create() {
        return CaseworkerAssignRequest.builder()
                .caseworkerId(BaseIntegrationTest.CaseworkerJohnDoe.getId())
                .build();
    }

    public CaseworkerAssignRequest create(Consumer<CaseworkerAssignRequest.Builder> customiser) {
        CaseworkerAssignRequest entity = create();
        CaseworkerAssignRequest.Builder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }
}

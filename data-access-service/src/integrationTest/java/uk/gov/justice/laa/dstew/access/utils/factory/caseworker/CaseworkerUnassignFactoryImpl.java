package uk.gov.justice.laa.dstew.access.utils.factory.caseworker;

import uk.gov.justice.laa.dstew.access.model.CaseworkerUnassignRequest;
import uk.gov.justice.laa.dstew.access.model.EventHistory;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

import java.util.function.Consumer;

public class CaseworkerUnassignFactoryImpl implements Factory<CaseworkerUnassignRequest, CaseworkerUnassignRequest.Builder> {
    @Override
    public CaseworkerUnassignRequest create() {
        return CaseworkerUnassignRequest.builder()
                .eventHistory(EventHistory.builder()
                    .eventDescription("Testing unassignment")
                    .build())
                .build();
    }

    public CaseworkerUnassignRequest create(Consumer<CaseworkerUnassignRequest.Builder> customiser) {
        CaseworkerUnassignRequest entity = create();
        CaseworkerUnassignRequest.Builder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }
}

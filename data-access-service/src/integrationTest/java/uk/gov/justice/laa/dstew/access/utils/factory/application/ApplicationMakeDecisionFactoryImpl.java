package uk.gov.justice.laa.dstew.access.utils.factory.application;

import java.util.function.Consumer;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

public class ApplicationMakeDecisionFactoryImpl implements Factory<MakeDecisionRequest, MakeDecisionRequest.Builder> {

    @Override
    public MakeDecisionRequest create() {
        return MakeDecisionRequest.builder()
                .build();
    }

    public MakeDecisionRequest create(Consumer<MakeDecisionRequest.Builder> customiser) {
        MakeDecisionRequest entity = create();
        MakeDecisionRequest.Builder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }

}

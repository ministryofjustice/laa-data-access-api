package uk.gov.justice.laa.dstew.access.utils.factory.application;

import java.util.function.Consumer;
import uk.gov.justice.laa.dstew.access.model.AssignDecisionRequest;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

public class ApplicationAssignDecisionFactoryImpl implements Factory<AssignDecisionRequest, AssignDecisionRequest.Builder> {

    @Override
    public AssignDecisionRequest create() {
        return AssignDecisionRequest.builder()
                .build();
    }

    public AssignDecisionRequest create(Consumer<AssignDecisionRequest.Builder> customiser) {
        AssignDecisionRequest entity = create();
        AssignDecisionRequest.Builder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }

}

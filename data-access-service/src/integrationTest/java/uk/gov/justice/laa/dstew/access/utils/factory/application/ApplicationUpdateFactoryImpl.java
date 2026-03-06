package uk.gov.justice.laa.dstew.access.utils.factory.application;

import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

import java.util.HashMap;
import java.util.function.Consumer;

public class ApplicationUpdateFactoryImpl implements Factory<ApplicationUpdateRequest, ApplicationUpdateRequest.Builder> {

    @Override
    public ApplicationUpdateRequest create() {
        return ApplicationUpdateRequest.builder()
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .applicationContent(new HashMap<>() {
                    {
                        put("test", "value");
                    }
                })
                .build();
    }

    public ApplicationUpdateRequest create(Consumer<ApplicationUpdateRequest.Builder> customiser) {
        ApplicationUpdateRequest entity = create();
        ApplicationUpdateRequest.Builder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }
}

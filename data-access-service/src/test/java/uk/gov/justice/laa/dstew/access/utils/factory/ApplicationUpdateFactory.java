package uk.gov.justice.laa.dstew.access.utils.factory;

import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class ApplicationUpdateFactory {

    public static ApplicationUpdateRequest create() {
        return ApplicationUpdateRequest.builder()
                .status(ApplicationStatus.IN_PROGRESS)
                .applicationContent(new HashMap<>(Map.of("test", "changed")))
                .build();
    }

    public static ApplicationUpdateRequest create(Consumer<ApplicationUpdateRequest.Builder> customiser) {
        ApplicationUpdateRequest entity = create();
        ApplicationUpdateRequest.Builder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }
}

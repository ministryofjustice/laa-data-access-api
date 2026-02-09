package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import java.util.HashMap;
import java.util.Map;

public class ApplicationUpdateRequestGenerator extends BaseGenerator<ApplicationUpdateRequest, ApplicationUpdateRequest.Builder> {
    public ApplicationUpdateRequestGenerator() {
        super(ApplicationUpdateRequest::toBuilder, ApplicationUpdateRequest.Builder::build);
    }

    @Override
    public ApplicationUpdateRequest createDefault() {
        return ApplicationUpdateRequest.builder()
                .status(ApplicationStatus.IN_PROGRESS)
                .applicationContent(new HashMap<>(Map.of("test", "changed")))
                .build();
    }
}


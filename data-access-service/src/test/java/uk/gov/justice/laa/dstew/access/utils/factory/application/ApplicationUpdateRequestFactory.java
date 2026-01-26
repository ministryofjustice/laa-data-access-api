package uk.gov.justice.laa.dstew.access.utils.factory.application;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

import java.util.HashMap;
import java.util.Map;

@Profile("unit-test")
@Component
public class ApplicationUpdateRequestFactory extends BaseFactory<ApplicationUpdateRequest, ApplicationUpdateRequest.Builder> {

    public ApplicationUpdateRequestFactory() {
        super(ApplicationUpdateRequest::toBuilder, ApplicationUpdateRequest.Builder::build);
    }

    @Override
    public ApplicationUpdateRequest createDefault() {
        return ApplicationUpdateRequest.builder()
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .applicationContent(new HashMap<>(Map.of("test", "changed")))
                .build();
    }
}

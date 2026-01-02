package uk.gov.justice.laa.dstew.access.utils.factory.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;
import uk.gov.justice.laa.dstew.access.utils.individual.IndividualFactory;

import java.util.List;

@Service
public class ApplicationCreateRequestFactory extends BaseFactory<ApplicationCreateRequest, ApplicationCreateRequest.Builder> {

    @Autowired
    private IndividualFactory individualFactory;

    public ApplicationCreateRequestFactory() {
        super(ApplicationCreateRequest::toBuilder, ApplicationCreateRequest.Builder::build);
    }

    @Override
    public ApplicationCreateRequest createDefault() {
        return ApplicationCreateRequest.builder()
                .status(ApplicationStatus.IN_PROGRESS)
                .laaReference("REF7327")
                .individuals(List.of(individualFactory.createDefault()))
                .applicationContent(new java.util.HashMap<>(java.util.Map.of("test", "content")))
                .build();
    }
}
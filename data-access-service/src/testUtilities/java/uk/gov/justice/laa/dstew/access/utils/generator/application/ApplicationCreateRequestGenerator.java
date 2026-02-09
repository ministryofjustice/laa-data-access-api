package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualGenerator;
import java.util.List;

public class ApplicationCreateRequestGenerator extends BaseGenerator<ApplicationCreateRequest, ApplicationCreateRequest.Builder> {
    private final IndividualGenerator individualGenerator = new IndividualGenerator();
    private final ApplicationContentGenerator applicationContentGenerator = new ApplicationContentGenerator();

    public ApplicationCreateRequestGenerator() {
        super(ApplicationCreateRequest::toBuilder, ApplicationCreateRequest.Builder::build);
    }

    @Override
    public ApplicationCreateRequest createDefault() {
        return ApplicationCreateRequest.builder()
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .laaReference("REF7327")
                .individuals(List.of(individualGenerator.createDefault()))
                .applicationContent(java.util.Map.of("applicationContent", applicationContentGenerator.createDefault()))
                .build();
    }
}

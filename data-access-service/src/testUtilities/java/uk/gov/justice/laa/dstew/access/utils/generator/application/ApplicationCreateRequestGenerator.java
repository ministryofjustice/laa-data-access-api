package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplyApplication;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.ApplicationCreateRequestIndividualGenerator;

public class ApplicationCreateRequestGenerator extends BaseGenerator<ApplicationCreateRequest, ApplicationCreateRequest.Builder> {
    private final ApplicationCreateRequestIndividualGenerator individualGenerator =
            new ApplicationCreateRequestIndividualGenerator();

    public ApplicationCreateRequestGenerator() {
        super(ApplicationCreateRequest::toBuilder, ApplicationCreateRequest.Builder::build);
    }

    @Override
    public ApplicationCreateRequest createDefault() {
      ApplyApplication applyApplication = new ApplyApplication();
      applyApplication.setObjectType("apply");
      applyApplication.setId(UUID.randomUUID());
      applyApplication.setSubmittedAt(OffsetDateTime.parse("2026-01-15T10:20:30Z"));

        return ApplicationCreateRequest.builder()
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .laaReference("REF7327")
                .individuals(List.of(individualGenerator.createDefault()))
                .applicationContent(applyApplication)
                .build();
    }
}

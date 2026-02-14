package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingGenerator;

import java.util.List;
import java.util.UUID;

public class ApplicationContentGenerator extends BaseGenerator<ApplicationContent, ApplicationContent.ApplicationContentBuilder> {
    private final ProceedingGenerator proceedingGenerator = new ProceedingGenerator();

    public ApplicationContentGenerator() {
        super(ApplicationContent::toBuilder, ApplicationContent.ApplicationContentBuilder::build);
    }

    @Override
    public ApplicationContent createDefault() {
        UUID applicationId = UUID.randomUUID();
        return ApplicationContent.builder()
                .id(applicationId)
                .submittedAt("2024-01-01T12:00:00Z")
                .proceedings(List.of(proceedingGenerator.createDefault()))
                .build();
    }
}

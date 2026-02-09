package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ApplicationContentGenerator extends BaseGenerator<ApplicationContent, ApplicationContent.ApplicationContentBuilder> {
    private final ProceedingGenerator proceedingDtoGenerator = new ProceedingGenerator();

    public ApplicationContentGenerator() {
        super(ApplicationContent::toBuilder, ApplicationContent.ApplicationContentBuilder::build);
    }

    @Override
    public ApplicationContent createDefault() {
        UUID applicationId = UUID.randomUUID();
        return ApplicationContent.builder()
                .id(applicationId)
                .submittedAt(Instant.now().toString())
                .proceedings(List.of(proceedingDtoGenerator.createDefault()))
                .build();
    }
}

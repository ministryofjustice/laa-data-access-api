package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.model.ApplicationContentDetails;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ApplicationContentGenerator extends BaseGenerator<ApplicationContentDetails, ApplicationContentDetails.ApplicationContentDetailsBuilder> {
    private final ProceedingDtoGenerator proceedingDtoGenerator = new ProceedingDtoGenerator();

    public ApplicationContentGenerator() {
        super(ApplicationContentDetails::toBuilder, ApplicationContentDetails.ApplicationContentDetailsBuilder::build);
    }

    @Override
    public ApplicationContentDetails createDefault() {
        UUID applicationId = UUID.randomUUID();
        return ApplicationContentDetails.builder()
                .id(applicationId)
                .autoGrant(true)
                .submittedAt(Instant.now())
                .proceedings(List.of(proceedingDtoGenerator.createDefault()))
                .build();
    }
}

package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.projection.ApplicationSummaryResult;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;
import java.time.Instant;
import java.util.UUID;

public class ApplicationSummaryGenerator extends BaseGenerator<ApplicationSummaryResult, ApplicationSummaryResult.ApplicationSummaryResultBuilder> {
    private final CaseworkerGenerator caseworkerGenerator = new CaseworkerGenerator();

    public ApplicationSummaryGenerator() {
        super(ApplicationSummaryResult::toBuilder, ApplicationSummaryResult.ApplicationSummaryResultBuilder::build);
    }

    @Override
    public ApplicationSummaryResult createDefault() {
        return ApplicationSummaryResult.builder()
                .id(UUID.randomUUID())
                .laaReference("REF7327")
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
//                .createdAt(Instant.now())
                .modifiedAt(Instant.now())
//                .caseworker(caseworkerGenerator.createDefault())
                .build();
    }

    @Override
    public ApplicationSummaryResult createRandom() {
        return createDefault().toBuilder()
                .laaReference(faker.bothify("REF####"))
                .build();
    }
}


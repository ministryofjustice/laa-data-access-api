package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public class ApplicationSummaryGenerator extends BaseGenerator<ApplicationSummary, ApplicationSummary.Builder> {
    private final CaseworkerGenerator caseworkerGenerator = new CaseworkerGenerator();

    public ApplicationSummaryGenerator() {
        super(ApplicationSummary::toBuilder, ApplicationSummary.Builder::build);
    }

    @Override
    public ApplicationSummary createDefault() {
        return ApplicationSummary.builder()
            .applicationId(UUID.randomUUID())
            .laaReference("REF7327")
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .lastUpdated(Instant.now().atOffset(ZoneOffset.UTC))
            .assignedTo(caseworkerGenerator.createDefault().getId())
            .matterType(MatterType.SCA)
            .applicationType(ApplicationType.INITIAL)
            .build();
    }

    @Override
    public ApplicationSummary createRandom() {
        return createDefault().toBuilder()
            .laaReference(faker.bothify("REF####"))
            .build();
    }
}
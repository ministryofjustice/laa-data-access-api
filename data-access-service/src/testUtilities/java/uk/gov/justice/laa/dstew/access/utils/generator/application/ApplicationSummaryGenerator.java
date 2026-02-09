package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;
import java.time.Instant;
import java.util.UUID;

public class ApplicationSummaryGenerator extends BaseGenerator<ApplicationSummaryEntity, ApplicationSummaryEntity.ApplicationSummaryEntityBuilder> {
    private final CaseworkerGenerator caseworkerGenerator = new CaseworkerGenerator();

    public ApplicationSummaryGenerator() {
        super(ApplicationSummaryEntity::toBuilder, ApplicationSummaryEntity.ApplicationSummaryEntityBuilder::build);
    }

    @Override
    public ApplicationSummaryEntity createDefault() {
        return ApplicationSummaryEntity.builder()
                .id(UUID.randomUUID())
                .laaReference("REF7327")
                .status(ApplicationStatus.IN_PROGRESS)
                .createdAt(Instant.now())
                .modifiedAt(Instant.now())
                .caseworker(caseworkerGenerator.createDefault())
                .build();
    }

    @Override
    public ApplicationSummaryEntity createRandom() {
        return createDefault().toBuilder()
                .laaReference(faker.bothify("REF####"))
                .build();
    }
}


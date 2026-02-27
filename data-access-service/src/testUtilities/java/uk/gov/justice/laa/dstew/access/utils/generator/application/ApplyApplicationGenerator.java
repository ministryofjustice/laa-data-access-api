package uk.gov.justice.laa.dstew.access.utils.generator.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplyApplication;
import uk.gov.justice.laa.dstew.access.model.BaseApplicationContent;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingGenerator;

public class ApplyApplicationGenerator extends BaseGenerator<ApplyApplication, ApplyApplication.Builder> {
    private final ProceedingGenerator proceedingDtoGenerator = new ProceedingGenerator();

    public ApplyApplicationGenerator() {
        super(ApplyApplication::toBuilder, ApplyApplication.Builder::build);
    }

    @Override
    public ApplyApplication createDefault() {
        UUID applicationId = UUID.randomUUID();
        return ApplyApplication.builder()
                .id(applicationId)
                .submittedAt(OffsetDateTime.parse("2024-01-01T12:00:00Z"))
                .build().putAdditionalProperty(
                    "proceedings", List.of(proceedingDtoGenerator.createDefault()));

    }
}

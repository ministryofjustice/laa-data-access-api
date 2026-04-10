package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;
import java.time.Instant;
import java.util.UUID;

public class ApplicationSummaryGenerator extends BaseGenerator<ApplicationSummaryDto, ApplicationSummaryDto.ApplicationSummaryDtoBuilder> {
    private final CaseworkerGenerator caseworkerGenerator = new CaseworkerGenerator();

    public ApplicationSummaryGenerator() {
        super(ApplicationSummaryDto::toBuilder, ApplicationSummaryDto.ApplicationSummaryDtoBuilder::build);
    }

    @Override
    public ApplicationSummaryDto createDefault() {
        return ApplicationSummaryDto.builder()
                .id(UUID.randomUUID())
                .laaReference("REF7327")
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .officeCode("12345")
                .submittedAt(Instant.now())
                .modifiedAt(Instant.now())
                .usedDelegatedFunctions(false)
                .categoryOfLaw(null)
                .matterType(null)
                .isAutoGranted(false)
                .isLead(false)
                .caseworkerId(caseworkerGenerator.createDefault().getId())
                .build();
    }

    @Override
    public ApplicationSummaryDto createRandom() {
        return createDefault().toBuilder()
                .laaReference(faker.bothify("REF####"))
                .build();
    }
}


package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.model.ApplicationApplicant;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingGenerator;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ApplicationContentGenerator extends BaseGenerator<ApplicationContent, ApplicationContent.ApplicationContentBuilder> {
    private final ProceedingGenerator proceedingDtoGenerator = new ProceedingGenerator();
    private final ApplicationMeritsGenerator meritsGenerator = new ApplicationMeritsGenerator();

    public ApplicationContentGenerator() {
        super(ApplicationContent::toBuilder, ApplicationContent.ApplicationContentBuilder::build);
    }

    @Override
    public ApplicationContent createDefault() {
        UUID applicationId = UUID.randomUUID();
        return ApplicationContent.builder()
                .id(applicationId)
                .submittedAt("2024-01-01T12:00:00Z")
                .previousApplicationReference("ZZ999Z")
                .lastNameAtBirth("Alberts")
                .relationshipToChildren("relationshipToChildren")
                .correspondenceAddressType("Home")
                .applicant(ApplicationApplicant.builder()
                        .addresses(List.of(
                            Map.of("k1", "v1"),
                            Map.of("k2", "v2")
                        ))
                        .build())
                .applicationMerits(meritsGenerator.createDefault())
                .proceedings(List.of(proceedingDtoGenerator.createDefault()))
                .submitterEmail("test@example.com")
                .build();

    }
}

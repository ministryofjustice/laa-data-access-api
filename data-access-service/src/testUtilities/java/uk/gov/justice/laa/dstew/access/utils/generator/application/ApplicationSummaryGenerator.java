package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.caseworker.CaseworkerGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public class ApplicationSummaryGenerator extends BaseGenerator<ApplicationEntity, ApplicationEntity.ApplicationEntityBuilder> {
    private final CaseworkerGenerator caseworkerGenerator = new CaseworkerGenerator();
    private final IndividualEntityGenerator individualEntityGenerator = new IndividualEntityGenerator();

    public ApplicationSummaryGenerator() {
        super(ApplicationEntity::toBuilder, ApplicationEntity.ApplicationEntityBuilder::build);
    }

    @Override
    public ApplicationEntity createDefault() {
        return ApplicationEntity.builder()
                .id(UUID.randomUUID())
                .laaReference("REF7327")
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .createdAt(Instant.now())
                .modifiedAt(Instant.now())
                .caseworker(caseworkerGenerator.createDefault())
                .individuals(Set.of(individualEntityGenerator.createDefault()))
                .build();
    }

    @Override
    public ApplicationEntity createRandom() {
        return createDefault().toBuilder()
                .laaReference(faker.bothify("REF####"))
                .build();
    }
}

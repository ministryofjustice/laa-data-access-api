package uk.gov.justice.laa.dstew.access.utils.generator.application;

import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class ApplicationEntityGenerator extends BaseGenerator<ApplicationEntity, ApplicationEntity.ApplicationEntityBuilder> {
    private final IndividualEntityGenerator individualEntityGenerator = new IndividualEntityGenerator();
    private final ApplicationContentGenerator applicationContentGenerator = new ApplicationContentGenerator();

    public ApplicationEntityGenerator() {
        super(ApplicationEntity::toBuilder, ApplicationEntity.ApplicationEntityBuilder::build);
    }

    @Override
    public ApplicationEntity createDefault() {
        return ApplicationEntity.builder()
                .schemaVersion(1)
                .createdAt(Instant.now())
                .applyApplicationId(UUID.randomUUID())
                .status(ApplicationStatus.IN_PROGRESS)
                .modifiedAt(Instant.now())
                .submittedAt(Instant.now())
                .laaReference("REF7327")
                .individuals(new HashSet<>(java.util.List.of(individualEntityGenerator.createDefault())))
                .applicationContent(new HashMap<>(java.util.Map.of("applicationContent", applicationContentGenerator.createDefault())))
                .useDelegatedFunctions(false)
                .isAutoGranted(true)
                .categoryOfLaw(CategoryOfLaw.FAMILY)
                .matterType(MatterType.SCA)
                .build();
    }
}

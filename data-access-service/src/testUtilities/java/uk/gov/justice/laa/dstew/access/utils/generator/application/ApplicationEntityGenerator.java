package uk.gov.justice.laa.dstew.access.utils.generator.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.helpers.SpringContext;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public class ApplicationEntityGenerator extends BaseGenerator<ApplicationEntity, ApplicationEntity.ApplicationEntityBuilder> {
    private final IndividualEntityGenerator individualEntityGenerator = new IndividualEntityGenerator();
    private final ApplicationContentGenerator applicationContentGenerator = new ApplicationContentGenerator();

    public ApplicationEntityGenerator() {
        super(ApplicationEntity::toBuilder, ApplicationEntity.ApplicationEntityBuilder::build);
    }

    @Override
    public ApplicationEntity createDefault() {
        ObjectMapper mapper = SpringContext.getObjectMapper();

        return ApplicationEntity.builder()
                .schemaVersion(1)
                .createdAt(Instant.now())
                .applyApplicationId(UUID.randomUUID())
                .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
                .modifiedAt(Instant.now())
                .submittedAt(Instant.parse("2024-01-01T12:00:00Z"))
                .laaReference("REF7327")
                .individuals(new HashSet<>(java.util.List.of(individualEntityGenerator.createDefault())))
                .applicationContent(
                        new HashMap<>(
                                java.util.Map.of(
                                        "applicationContent",
                                        mapper.convertValue(applicationContentGenerator.createDefault(), Map.class))))
                .usedDelegatedFunctions(false)
                .isAutoGranted(true)
                .categoryOfLaw(CategoryOfLaw.FAMILY)
                .matterType(MatterType.SCA)
                .build();
    }
}

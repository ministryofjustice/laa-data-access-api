package uk.gov.justice.laa.dstew.access.utils.factory.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.individual.IndividualEntityFactory;

import java.time.InstantSource;
import java.util.*;

@Component
public class ApplicationEntityFactory extends BaseFactory<ApplicationEntity, ApplicationEntity.ApplicationEntityBuilder> {

    @Autowired
    private IndividualEntityFactory individualEntityFactory;

    public ApplicationEntityFactory() {
        super(ApplicationEntity::toBuilder, ApplicationEntity.ApplicationEntityBuilder::build);
    }

    @Override
    public ApplicationEntity createDefault() {
        return ApplicationEntity.builder()
                .schemaVersion(1)
                .createdAt(InstantSource.system().instant())
                .id(UUID.randomUUID())
                .status(ApplicationStatus.IN_PROGRESS)
                .modifiedAt(InstantSource.system().instant())
                .laaReference("REF7327")
                .individuals(Set.of(
                        individualEntityFactory.createDefault()
                ))
                .applicationContent(Map.of("test", "content"))
                .build();
    }

    @Override
    public ApplicationEntity createRandom() {
        return createDefault().toBuilder()
                .laaReference(faker.bothify("REF####"))
                .individuals(Set.of(
                        individualEntityFactory.createRandom()
                ))
                .applicationContent(Map.of(
                        "test", faker.text().text(50)
                ))
                .build();
    }
}
package uk.gov.justice.laa.dstew.access.utils.factory.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.caseworker.CaseworkerFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.individual.IndividualEntityFactory;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Profile("unit-test")
@Component
public class ApplicationSummaryFactory extends BaseFactory<ApplicationEntity, ApplicationEntity.ApplicationEntityBuilder> {

    @Autowired
    private CaseworkerFactory caseworkerFactory;

    @Autowired
    private IndividualEntityFactory individualEntityFactory;

    public ApplicationSummaryFactory() {
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
                .caseworker(caseworkerFactory.createDefault())
                .individuals(Set.of(individualEntityFactory.createDefault()))
                .build();
    }

    @Override
    public ApplicationEntity createRandom() {
        return createDefault().toBuilder()
                .laaReference(faker.bothify("REF####"))
                .build();
    }
}
package uk.gov.justice.laa.dstew.access.utils.factory.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationSummaryEntity;
import uk.gov.justice.laa.dstew.access.model.Status;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.caseworker.CaseworkerFactory;

import java.time.Instant;
import java.util.UUID;

@Profile("unit-test")
@Component
public class ApplicationSummaryFactory extends BaseFactory<ApplicationSummaryEntity, ApplicationSummaryEntity.ApplicationSummaryEntityBuilder> {

    @Autowired
    private CaseworkerFactory caseworkerFactory;

    public ApplicationSummaryFactory() {
        super(ApplicationSummaryEntity::toBuilder, ApplicationSummaryEntity.ApplicationSummaryEntityBuilder::build);
    }

    @Override
    public ApplicationSummaryEntity createDefault() {
        return ApplicationSummaryEntity.builder()
                .id(UUID.randomUUID())
                .laaReference("REF7327")
                .status(Status.IN_PROGRESS)
                .createdAt(Instant.now())
                .modifiedAt(Instant.now())
                .caseworker(caseworkerFactory.createDefault())
                .build();
    }

    @Override
    public ApplicationSummaryEntity createRandom() {
        return createDefault().toBuilder()
                .laaReference(faker.bothify("REF####"))
                .build();
    }
}
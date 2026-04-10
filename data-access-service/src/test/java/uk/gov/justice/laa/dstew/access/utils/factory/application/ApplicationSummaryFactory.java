package uk.gov.justice.laa.dstew.access.utils.factory.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.caseworker.CaseworkerFactory;

import java.time.Instant;
import java.util.UUID;

@Profile("unit-test")
@Component
public class ApplicationSummaryFactory extends BaseFactory<ApplicationSummaryDto, ApplicationSummaryDto.ApplicationSummaryDtoBuilder> {

    @Autowired
    private CaseworkerFactory caseworkerFactory;

    public ApplicationSummaryFactory() {
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
                .caseworkerId(caseworkerFactory.createDefault().getId())
                .build();
    }

    @Override
    public ApplicationSummaryDto createRandom() {
        return createDefault().toBuilder()
                .laaReference(faker.bothify("REF####"))
                .build();
    }
}
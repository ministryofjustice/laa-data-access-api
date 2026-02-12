package uk.gov.justice.laa.dstew.access.utils.factory.application;

import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.utils.factory.caseworker.CaseworkerFactory;

import java.time.Instant;
import java.util.UUID;

@Profile("unit-test")
@Component
public class ApplicationSummaryFactory {

    @Autowired
    private CaseworkerFactory caseworkerFactory;

    public ApplicationSummary createDefault() {
        ApplicationSummary summary = new ApplicationSummary();
        summary.setApplicationId(UUID.randomUUID());
        summary.setLaaReference("REF7327");
        summary.setStatus(ApplicationStatus.APPLICATION_IN_PROGRESS);
        summary.setLastUpdated(Instant.now().atOffset(ZoneOffset.UTC));
        summary.setAssignedTo(caseworkerFactory.createDefault().getId());
        summary.setMatterType(MatterType.SCA);
        summary.setApplicationType(ApplicationType.INITIAL);
        return summary;
    }

    public ApplicationSummary createRandom() {
        ApplicationSummary summary = createDefault();
        summary.setLaaReference("REF" + UUID.randomUUID().toString().substring(0,4));
        return summary;
    }

    public List<ApplicationSummary> createMultipleRandom(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> createRandom())
            .collect(Collectors.toList());
    }
}
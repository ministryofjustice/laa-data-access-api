package uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Component
public class ApplicationAsserts {

    @Autowired
    private ApplicationRepository applicationRepository;

    public void assertApplicationsMatchInRepository(List<ApplicationEntity> expected) {
        List<ApplicationEntity> actual = applicationRepository.findAllById(
                expected.stream().map(ApplicationEntity::getId).collect(Collectors.toList()));

        assertThat(expected.size()).isEqualTo(actual.size());

        List<Application> actualApplications = actual.stream()
                .map(this::createApplication)
                .toList();

        List<Application> expectedApplications = expected.stream()
                .map(this::createApplication)
                .toList();

        assertTrue(expectedApplications.containsAll(actualApplications));
    }

    public Application createApplication(ApplicationEntity applicationEntity) {
        Application application = new Application();
        application.setApplicationId(applicationEntity.getId());
        application.setStatus(applicationEntity.getStatus());
        if (applicationEntity.getCaseworker() != null) {
            application.setAssignedTo(applicationEntity.getCaseworker().getId());
        }
        application.setLastUpdated(OffsetDateTime.ofInstant(applicationEntity.getUpdatedAt(), ZoneOffset.UTC));
        application.setSubmittedAt(
                applicationEntity.getSubmittedAt() != null
                        ? OffsetDateTime.ofInstant(applicationEntity.getSubmittedAt(), ZoneOffset.UTC)
                        : null
        );
        application.setUseDelegatedFunctions(applicationEntity.getUsedDelegatedFunctions());
        application.setAutoGrant(applicationEntity.getIsAutoGranted());
        if (applicationEntity.getDecision() != null) {
            application.setOverallDecision(applicationEntity.getDecision().getOverallDecision());
        }
        return application;
    }

    public Application createApplicationIgnoreLastUpdated(ApplicationEntity applicationEntity) {
        Application application = createApplication(applicationEntity);
        assertNotNull(applicationEntity.getModifiedAt());
        application.setLastUpdated(null);
        return application;
    }
}

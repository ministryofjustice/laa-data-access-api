package uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts;

import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Component
public class ApplicationAsserts {

    @Autowired
    private ApplicationRepository applicationRepository;

    public void assertErrorGeneratedByBadHeader(MvcResult result, String serviceName) {
        String errorMessage;
        assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
        Exception exception = result.getResolvedException();

        switch (serviceName) {
            case null:
                errorMessage = "Required request header 'X-Service-Name' for method parameter type ServiceName is not present";
              break;
            case "":
                errorMessage =
                "Required request header 'X-Service-Name' for method parameter type ServiceName is present but converted to null";
              break;
            default:
                errorMessage =
                "Failed to convert value of type 'java.lang.String' to required type 'uk.gov.justice.laa.dstew.access.model.ServiceName'";
        }

        Assertions.assertThat(exception.getMessage()).contains(errorMessage);
    }


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

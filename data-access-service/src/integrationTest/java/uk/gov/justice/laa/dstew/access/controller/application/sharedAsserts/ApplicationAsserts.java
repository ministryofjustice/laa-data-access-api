package uk.gov.justice.laa.dstew.access.controller.application.sharedAsserts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.Application;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;

@Component
public class ApplicationAsserts {

  @Autowired
  private ApplicationRepository applicationRepository;

  public void assertErrorGeneratedByBadHeader(MvcResult result, String serviceName) {
    String errorMessage;
    assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
    Exception exception = result.getResolvedException();

    errorMessage = switch (serviceName) {
      case null -> "Required request header 'X-Service-Name' for method parameter type ServiceName is not present";
      case "" ->
          "Required request header 'X-Service-Name' for method parameter type ServiceName is present but converted to null";
      default ->
          "Failed to convert value of type 'java.lang.String' to required type 'uk.gov.justice.laa.dstew.access.model.ServiceName'";
    };

    Assertions.assertThat(exception.getMessage()).contains(errorMessage);
  }


  public void assertApplicationsMatchInRepository(List<ApplicationEntity> expected) {
    List<ApplicationEntity> actual = applicationRepository.findAllById(
        expected.stream().map(ApplicationEntity::getId).collect(Collectors.toList()));

    assertEquals(expected.size(), (actual.size()));

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
    application.setLastUpdated(getOffsetDateTime(applicationEntity.getUpdatedAt()));
    application.setSubmittedAt(
        applicationEntity.getSubmittedAt() != null
            ? getOffsetDateTime(applicationEntity.getSubmittedAt())
            : null
    );
    application.setUsedDelegatedFunctions(applicationEntity.getUsedDelegatedFunctions());
    application.setAutoGrant(applicationEntity.getIsAutoGranted());
    if (applicationEntity.getDecision() != null) {
      application.setOverallDecision(applicationEntity.getDecision().getOverallDecision());
    }
    return application;
  }

  private static @NonNull OffsetDateTime getOffsetDateTime(Instant instant) {
    return OffsetDateTime.ofInstant(instant.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
  }

  public Application createApplicationIgnoreLastUpdated(ApplicationEntity applicationEntity) {
    Application application = createApplication(applicationEntity);
    assertNotNull(applicationEntity.getModifiedAt());
    application.setLastUpdated(null);
    return application;
  }
}

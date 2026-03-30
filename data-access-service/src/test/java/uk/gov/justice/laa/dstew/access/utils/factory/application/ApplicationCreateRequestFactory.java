package uk.gov.justice.laa.dstew.access.utils.factory.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplyApplication;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;
import uk.gov.justice.laa.dstew.access.utils.factory.individual.ApplicationCreateRequestIndividualFactory;

@Profile("unit-test")
@Component
public class ApplicationCreateRequestFactory extends BaseFactory<ApplicationCreateRequest, ApplicationCreateRequest.Builder> {

  @Autowired
  private ApplicationCreateRequestIndividualFactory individualFactory;

  public ApplicationCreateRequestFactory() {
    super(ApplicationCreateRequest::toBuilder, ApplicationCreateRequest.Builder::build);
  }

  @Override
  public ApplicationCreateRequest createDefault() {

    ApplyApplication applyApplication = new ApplyApplication();
    applyApplication.setObjectType("apply");
    applyApplication.setId(UUID.randomUUID());
    applyApplication.setSubmittedAt(OffsetDateTime.parse("2026-01-15T10:20:30Z"));

    return ApplicationCreateRequest.builder()
        .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
        .laaReference("REF7327")
        .individuals(List.of(individualFactory.createDefault()))
        .applicationContent(applyApplication)
        .build();
  }
}
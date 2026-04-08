package uk.gov.justice.laa.dstew.access.utils.factory.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import uk.gov.justice.laa.dstew.access.model.ApplicationContent;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplyApplication;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

public class ApplicationCreateFactoryImpl implements Factory<ApplicationCreateRequest, ApplicationCreateRequest.Builder> {

  ApplicationContentFactory applicationContentFactory = new ApplicationContentFactory();

  @Override
  public ApplicationCreateRequest create() {
    ApplicationContent content = applicationContentFactory.create();
    ApplyApplication applyApp = new ApplyApplication();
    applyApp.setObjectType("apply");
    applyApp.setId(content.getId());
    applyApp.setSubmittedAt(OffsetDateTime.parse(content.getSubmittedAt()));

    return ApplicationCreateRequest.builder()
        .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
        .laaReference("TestReference")
        .applicationContent(applyApp)
        .individuals(List.of(
            IndividualCreateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.now())
                .details(Map.of(
                    "test", "content"
                ))
                .type(IndividualType.CLIENT)
                .build()
        ))
        .build();
  }

  public ApplicationCreateRequest create(Consumer<ApplicationCreateRequest.Builder> customiser) {
    ApplicationCreateRequest entity = create();
    ApplicationCreateRequest.Builder builder = entity.toBuilder();
    customiser.accept(builder);
    return builder.build();
  }
}

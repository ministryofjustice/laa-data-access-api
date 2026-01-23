package uk.gov.justice.laa.dstew.access.utils.factory.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.RequestApplicationContent;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

public class ApplicationCreateFactoryImpl implements Factory<ApplicationCreateRequest, ApplicationCreateRequest.Builder> {

  ApplicationContentFactory applicationContentFactory = new ApplicationContentFactory();

  @Override
  public ApplicationCreateRequest create() {

    RequestApplicationContent requestApplicationContent = RequestApplicationContent.builder()
        .applicationContent(applicationContentFactory.create())
        .build();

    return ApplicationCreateRequest.builder()
        .status(ApplicationStatus.IN_PROGRESS)
        .laaReference("TestReference")
        .applicationContent(requestApplicationContent)
        .individuals(List.of(
            Individual.builder()
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

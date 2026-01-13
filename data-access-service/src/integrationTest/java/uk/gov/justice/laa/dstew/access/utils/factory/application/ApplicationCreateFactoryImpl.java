package uk.gov.justice.laa.dstew.access.utils.factory.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import uk.gov.justice.laa.dstew.access.enums.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.enums.MatterType;
import uk.gov.justice.laa.dstew.access.model.ApplicationContentDetails;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.ProceedingDetails;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

public class ApplicationCreateFactoryImpl implements Factory<ApplicationCreateRequest, ApplicationCreateRequest.Builder> {

  @Override
  public ApplicationCreateRequest create() {
    ProceedingDetails proceedingDetails = ProceedingDetails
        .builder()
        .id(UUID.randomUUID())
        .categoryOfLaw(CategoryOfLaw.Family)
        .matterType(MatterType.SCA)
        .leadProceeding(true)
        .useDelegatedFunctions(true)
        .build();

    ApplicationContentDetails applicationContentDetails = ApplicationContentDetails.builder()
        .applyApplicationId(UUID.randomUUID())
        .autoGrant(true)
        .proceedings(List.of(proceedingDetails))
        .build();
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    objectMapper.registerModule(new JavaTimeModule());
    Map<String, Object> applicationContent = objectMapper.convertValue(applicationContentDetails, Map.class);
    applicationContent.put("test", "value");
    return ApplicationCreateRequest.builder()
        .status(ApplicationStatus.IN_PROGRESS)
        .laaReference("TestReference")
        .applicationContent(applicationContent)
        .individuals(List.of(
            Individual.builder()
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.now())
                .details(Map.of(
                    "test", "content"
                ))
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

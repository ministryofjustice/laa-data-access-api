package uk.gov.justice.laa.dstew.access.utils.factory.application;

import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ApplicationCreateFactoryImpl implements Factory<ApplicationCreateRequest, ApplicationCreateRequest.Builder> {

    @Override
    public ApplicationCreateRequest create() {
        return ApplicationCreateRequest.builder()
                .status(ApplicationStatus.IN_PROGRESS)
                .applicationReference("TestReference")
                .applicationContent(new HashMap<>() {
                    {
                        put("test", "value");
                    }
                })
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

    @Override
    public ApplicationCreateRequest create(Consumer<ApplicationCreateRequest.Builder> customiser) {
        ApplicationCreateRequest entity = create();
        ApplicationCreateRequest.Builder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }

//    public ApplicationCreateRequest create(
//            ApplicationStatus status = ApplicationStatus.IN_PROGRESS,
//            Integer schemaVersion = 1,
//            Map<String, Object> applicationContent =
//                    new HashMap<String, Object>() { {"test", new String("test"} }
//    ) {
//        return new ApplicationCreateRequest().builder()
//                .status(status)
//                .schemaVersion(schemaVersion)
//                .applicationContent(applicationContent)
//                .build();
//    }
}

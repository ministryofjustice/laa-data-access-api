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
                .laaReference("TestReference")
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

    public ApplicationCreateRequest create(Consumer<ApplicationCreateRequest.Builder> customiser) {
        ApplicationCreateRequest entity = create();
        ApplicationCreateRequest.Builder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }
}

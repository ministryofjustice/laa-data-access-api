package uk.gov.justice.laa.dstew.access.utils.factory.individual;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequestIndividual;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class ApplicationCreateRequestIndividualFactoryImpl
        implements Factory<ApplicationCreateRequestIndividual, ApplicationCreateRequestIndividual.Builder> {

    @Override
    public ApplicationCreateRequestIndividual create() {
        return ApplicationCreateRequestIndividual.builder()
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.now())
                .details(Map.of(
                        "test", "content"
                ))
                .type(IndividualType.CLIENT)
                .build();
    }

    @Override
    public ApplicationCreateRequestIndividual create(Consumer<ApplicationCreateRequestIndividual.Builder> customiser) {
        ApplicationCreateRequestIndividual individual = create();
        ApplicationCreateRequestIndividual.Builder builder = individual.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }
}
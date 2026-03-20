package uk.gov.justice.laa.dstew.access.utils.factory.individual;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequestIndividual;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Profile("unit-test")
@Component
public class ApplicationCreateRequestIndividualFactory
        extends BaseFactory<ApplicationCreateRequestIndividual, ApplicationCreateRequestIndividual.Builder> {

    public ApplicationCreateRequestIndividualFactory() {
        super(ApplicationCreateRequestIndividual::toBuilder, ApplicationCreateRequestIndividual.Builder::build);
    }

    @Override
    public ApplicationCreateRequestIndividual createDefault() {
        return ApplicationCreateRequestIndividual.builder()
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.now())
                .details(new HashMap<>(Map.of("test", "content")))
                .build();
    }

    @Override
    public ApplicationCreateRequestIndividual createRandom() {
        return ApplicationCreateRequestIndividual.builder()
                .firstName(faker.name().firstName())
                .lastName(faker.name().lastName())
                .dateOfBirth(getRandomDate())
                .details(new HashMap<>(Map.of("test", "content")))
                .build();
    }
}
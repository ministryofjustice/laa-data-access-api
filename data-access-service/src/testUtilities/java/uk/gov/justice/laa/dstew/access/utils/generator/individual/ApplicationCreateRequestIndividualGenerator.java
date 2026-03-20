package uk.gov.justice.laa.dstew.access.utils.generator.individual;

import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequestIndividual;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class ApplicationCreateRequestIndividualGenerator
        extends BaseGenerator<ApplicationCreateRequestIndividual, ApplicationCreateRequestIndividual.Builder> {
    public ApplicationCreateRequestIndividualGenerator() {
        super(ApplicationCreateRequestIndividual::toBuilder, ApplicationCreateRequestIndividual.Builder::build);
    }

    @Override
    public ApplicationCreateRequestIndividual createDefault() {
        return ApplicationCreateRequestIndividual.builder()
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.now())
                .details(new HashMap<>(Map.of("test", "content")))
                .type(IndividualType.CLIENT)
                .build();
    }

    @Override
    public ApplicationCreateRequestIndividual createRandom() {
        return ApplicationCreateRequestIndividual.builder()
                .firstName(faker.name().firstName())
                .lastName(faker.name().lastName())
                .dateOfBirth(getRandomDate())
                .details(new HashMap<>(Map.of("test", "content")))
                .type(IndividualType.CLIENT)
                .build();
    }
}

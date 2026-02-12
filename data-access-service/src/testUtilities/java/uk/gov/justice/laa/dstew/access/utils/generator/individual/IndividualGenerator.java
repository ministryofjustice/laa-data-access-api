package uk.gov.justice.laa.dstew.access.utils.generator.individual;

import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class IndividualGenerator extends BaseGenerator<Individual, Individual.Builder> {
    public IndividualGenerator() {
        super(Individual::toBuilder, Individual.Builder::build);
    }

    @Override
    public Individual createDefault() {
        return Individual.builder()
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.now())
                .details(new HashMap<>(Map.of("test", "content")))
                .type(IndividualType.CLIENT)
                .build();
    }

    @Override
    public Individual createRandom() {
        return Individual.builder()
                .firstName(faker.name().firstName())
                .lastName(faker.name().lastName())
                .dateOfBirth(getRandomDate())
                .details(new HashMap<>(Map.of("test", "content")))
                .type(IndividualType.CLIENT)
                .build();
    }
}


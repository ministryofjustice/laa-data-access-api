package uk.gov.justice.laa.dstew.access.utils.individual;

import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
public class IndividualFactory extends BaseFactory<Individual, Individual.Builder> {

    public IndividualFactory() {
        super(Individual::toBuilder, Individual.Builder::build);
    }

    @Override
    public Individual createDefault() {
        return Individual.builder()
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.now())
                .details(new HashMap<>(Map.of("test", "content")))
                .build();
    }

    @Override
    public Individual createRandom() {
        return Individual.builder()
                .firstName(faker.name().firstName())
                .lastName(faker.name().lastName())
                .dateOfBirth(getRandomDate())
                .details(new HashMap<>(Map.of("test", "content")))
                .build();
    }
}
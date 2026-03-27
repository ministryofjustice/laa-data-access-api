package uk.gov.justice.laa.dstew.access.utils.generator.individual;

import uk.gov.justice.laa.dstew.access.model.IndividualResponse;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class IndividualGenerator extends BaseGenerator<IndividualResponse, IndividualResponse.Builder> {
    public IndividualGenerator() {
        super(IndividualResponse::toBuilder, IndividualResponse.Builder::build);
    }

    @Override
    public IndividualResponse createDefault() {
        return IndividualResponse.builder()
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.now())
                .details(new HashMap<>(Map.of("test", "content")))
                .type(IndividualType.CLIENT)
                .build();
    }

    @Override
    public IndividualResponse createRandom() {
        return IndividualResponse.builder()
                .firstName(faker.name().firstName())
                .lastName(faker.name().lastName())
                .dateOfBirth(getRandomDate())
                .details(new HashMap<>(Map.of("test", "content")))
                .type(IndividualType.CLIENT)
                .build();
    }
}


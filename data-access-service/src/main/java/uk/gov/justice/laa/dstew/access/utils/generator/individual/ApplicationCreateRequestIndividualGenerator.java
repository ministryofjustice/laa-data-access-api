package uk.gov.justice.laa.dstew.access.utils.generator.individual;

import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class ApplicationCreateRequestIndividualGenerator
        extends BaseGenerator<IndividualCreateRequest, IndividualCreateRequest.Builder> {
    public ApplicationCreateRequestIndividualGenerator() {
        super(IndividualCreateRequest::toBuilder, IndividualCreateRequest.Builder::build);
    }

    @Override
    public IndividualCreateRequest createDefault() {
        return IndividualCreateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.now())
                .details(new HashMap<>(Map.of("test", "content")))
                .type(IndividualType.CLIENT)
                .build();
    }

    @Override
    public IndividualCreateRequest createRandom() {
        return IndividualCreateRequest.builder()
                .firstName(faker.name().firstName())
                .lastName(faker.name().lastName())
                .dateOfBirth(getRandomDate())
                .details(new HashMap<>(Map.of("test", "content")))
                .type(IndividualType.CLIENT)
                .build();
    }
}

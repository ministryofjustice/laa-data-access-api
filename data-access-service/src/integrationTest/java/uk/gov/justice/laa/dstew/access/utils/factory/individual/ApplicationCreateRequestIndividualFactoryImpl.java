package uk.gov.justice.laa.dstew.access.utils.factory.individual;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class ApplicationCreateRequestIndividualFactoryImpl
        implements Factory<IndividualCreateRequest, IndividualCreateRequest.Builder> {

    @Override
    public IndividualCreateRequest create() {
        return IndividualCreateRequest.builder()
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
    public IndividualCreateRequest create(Consumer<IndividualCreateRequest.Builder> customiser) {
        IndividualCreateRequest individual = create();
        IndividualCreateRequest.Builder builder = individual.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }
}
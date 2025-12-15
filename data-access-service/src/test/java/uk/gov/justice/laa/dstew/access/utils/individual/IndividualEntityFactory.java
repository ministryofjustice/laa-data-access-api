package uk.gov.justice.laa.dstew.access.utils.individual;

import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;

import java.time.LocalDate;
import java.util.function.Consumer;

public class IndividualEntityFactory {

    public static IndividualEntity create() {
        return IndividualEntity.builder()
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.now())
                .build();
    }

    public static IndividualEntity create(Consumer<IndividualEntity.IndividualEntityBuilder> customiser) {
        IndividualEntity entity = create();
        IndividualEntity.IndividualEntityBuilder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }
}

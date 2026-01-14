package uk.gov.justice.laa.dstew.access.utils.factory.individual;

import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.utils.factory.Factory;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Consumer;

public class IndividualEntityFactoryImpl implements Factory<IndividualEntity, IndividualEntity.IndividualEntityBuilder> {
    @Override
    public IndividualEntity create() {
        return IndividualEntity.builder()
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.now())
                .individualContent(Map.of(
                        "test", "content"
                ))
                .type(IndividualType.CLIENT)
                .build();
    }

    @Override
    public IndividualEntity create(Consumer<IndividualEntity.IndividualEntityBuilder> customiser) {
        IndividualEntity entity = create();
        IndividualEntity.IndividualEntityBuilder builder = entity.toBuilder();
        customiser.accept(builder);
        return builder.build();
    }
}

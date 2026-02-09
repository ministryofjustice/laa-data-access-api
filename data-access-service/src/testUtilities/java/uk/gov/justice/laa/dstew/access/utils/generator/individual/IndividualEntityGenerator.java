package uk.gov.justice.laa.dstew.access.utils.generator.individual;

import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class IndividualEntityGenerator extends BaseGenerator<IndividualEntity, IndividualEntity.IndividualEntityBuilder> {
    public IndividualEntityGenerator() {
        super(IndividualEntity::toBuilder, IndividualEntity.IndividualEntityBuilder::build);
    }

    @Override
    public IndividualEntity createDefault() {
        return IndividualEntity.builder()
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.now())
                .individualContent(new HashMap<>(Map.of("test", "content")))
                .type(IndividualType.CLIENT)
                .build();
    }

    @Override
    public IndividualEntity createRandom() {
        return IndividualEntity.builder()
                .firstName(faker.name().firstName())
                .lastName(faker.name().lastName())
                .dateOfBirth(java.time.LocalDate.now().minusDays(faker.number().numberBetween(1, 10000)))
                .individualContent(new HashMap<>(Map.of("test", "content")))
                .type(IndividualType.CLIENT)
                .build();
    }
}


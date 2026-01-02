package uk.gov.justice.laa.dstew.access.utils.individual;

import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.utils.factory.BaseFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class IndividualEntityFactory extends BaseFactory<IndividualEntity, IndividualEntity.IndividualEntityBuilder> {

    public IndividualEntityFactory() {
        super(IndividualEntity::toBuilder, IndividualEntity.IndividualEntityBuilder::build);
    }

    @Override
    public IndividualEntity createDefault() {
        return IndividualEntity.builder()
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.now())
                .individualContent(new HashMap<>(Map.of("test", "content")))
                .build();
    }

    @Override
    public IndividualEntity createRandom() {
        return IndividualEntity.builder()
                .firstName(faker.name().firstName())
                .lastName(faker.name().lastName())
                .dateOfBirth(getRandomDate())
                .individualContent(new HashMap<>(Map.of("test", "content")))
                .build();
    }
}

package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualGenerator;

@ExtendWith(MockitoExtension.class)
class IndividualMapperTest extends BaseMapperTest {

    @InjectMocks
    private IndividualMapperImpl individualMapper;

    @Test
    void givenNullIndividualEntity_whenToIndividual_thenReturnNull() {
        assertThat(individualMapper.toIndividual(null)).isNull();
    }

    @Test
    void givenIndividualEntity_whenToIndividual_thenMapsFieldsCorrectly() {
        LocalDate dateOfBirth = LocalDate.of(1980, 5, 15);
        Map<String, Object> individualContent = Map.of("key", "value");

        IndividualEntity entity = DataGenerator.createDefault(IndividualEntityGenerator.class,
                builder -> builder
                        .firstName("John")
                        .lastName("Doe")
                        .dateOfBirth(dateOfBirth)
                        .individualContent(individualContent)
                        .type(IndividualType.CLIENT));

        Individual result = individualMapper.toIndividual(entity);

        assertThat(result).isNotNull();
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.getDateOfBirth()).isEqualTo(dateOfBirth);
        assertThat(result.getDetails()).isEqualTo(individualContent);
        assertThat(result.getType()).isEqualTo(IndividualType.CLIENT);
    }

    @Test
    void givenIndividualEntityWithAllNullFields_whenToIndividual_thenAllFieldsAreNull() {
        IndividualEntity entity = DataGenerator.createDefault(IndividualEntityGenerator.class,
                builder -> builder
                        .firstName(null)
                        .lastName(null)
                        .dateOfBirth(null)
                        .individualContent(null)
                        .type(null));

        Individual result = individualMapper.toIndividual(entity);

        assertThat(result.getFirstName()).isNull();
        assertThat(result.getLastName()).isNull();
        assertThat(result.getDateOfBirth()).isNull();
        assertThat(result.getDetails()).isNull();
        assertThat(result.getType()).isNull();
    }

    @Test
    void givenNullIndividual_whenToIndividualEntity_thenReturnNull() {
        assertThat(individualMapper.toIndividualEntity(null)).isNull();
    }

    @Test
    void givenIndividual_whenToIndividualEntity_thenMapsFieldsCorrectly() {
        LocalDate dateOfBirth = LocalDate.of(2025, 11, 24);
        Map<String, Object> details = Map.of("key", "value");

        Individual individual = DataGenerator.createDefault(IndividualGenerator.class,
                builder -> builder
                        .firstName("John")
                        .lastName("Doe")
                        .dateOfBirth(dateOfBirth)
                        .details(details)
                        .type(IndividualType.CLIENT));

        IndividualEntity result = individualMapper.toIndividualEntity(individual);

        assertThat(result).isNotNull();
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.getDateOfBirth()).isEqualTo(dateOfBirth);
        assertThat(result.getIndividualContent()).isEqualTo(details);
        assertThat(result.getType()).isEqualTo(IndividualType.CLIENT);
    }

    @Test
    void givenIndividualWithAllNullFields_whenToIndividualEntity_thenAllFieldsAreNull() {
        Individual individual = DataGenerator.createDefault(IndividualGenerator.class,
                builder -> builder
                        .firstName(null)
                        .lastName(null)
                        .dateOfBirth(null)
                        .details(null)
                        .type(null));

        IndividualEntity result = individualMapper.toIndividualEntity(individual);

        assertThat(result.getFirstName()).isNull();
        assertThat(result.getLastName()).isNull();
        assertThat(result.getDateOfBirth()).isNull();
        assertThat(result.getIndividualContent()).isNull();
        assertThat(result.getType()).isNull();
    }
}
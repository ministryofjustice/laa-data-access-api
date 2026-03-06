package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

class IndividualMapperTest {

    private final IndividualMapper individualMapper = Mappers.getMapper(IndividualMapper.class);

    @Test
    void givenNullIndividualEntity_whenToIndividual_thenReturnNull() {
        IndividualEntity entity = null;
        assertThat(individualMapper.toIndividual(entity)).isNull();
    }

    @Test
    void givenIndividualEntity_whenToIndividual_thenMapsFieldsCorrectly() {
        String firstName = "John";
        String lastName = "Doe";
        LocalDate dateOfBirth = LocalDate.of(1980, 5, 15);
        Map<String, Object> individualContent = Map.of("key", "value");
        IndividualType type = IndividualType.CLIENT;

        IndividualEntity expectedIndividualEntity = IndividualEntity.builder()
                .firstName(firstName)
                .lastName(lastName)
                .dateOfBirth(dateOfBirth)
                .individualContent(individualContent)
                .type(type)
                .build();

        Individual actualIndividual = individualMapper.toIndividual(expectedIndividualEntity);

        assertThat(actualIndividual).isNotNull();
        assertThat(actualIndividual.getFirstName()).isEqualTo(firstName);
        assertThat(actualIndividual.getLastName()).isEqualTo(lastName);
        assertThat(actualIndividual.getDateOfBirth()).isEqualTo(dateOfBirth);
        assertThat(actualIndividual.getDetails()).isEqualTo(individualContent);
        assertThat(actualIndividual.getType()).isEqualTo(type);
    }

    @Test
    void givenNullIndividual_whenToIndividualEntity_thenReturnNull() {
        Individual individual = null;
        assertThat(individualMapper.toIndividualEntity(individual)).isNull();
    }

    @Test
    void givenIndividual_whenToIndividualEntity_thenMapsFieldsCorrectly() {
        String firstName = "John";
        String lastName = "Doe";
        LocalDate dateOfBirth = LocalDate.of(2025, 11, 24);
        Map<String, Object> details = Map.of("key", "value");
        IndividualType type = IndividualType.CLIENT;

        Individual expectedIndividual = Individual.builder()
                .firstName(firstName)
                .lastName(lastName)
                .dateOfBirth(dateOfBirth)
                .details(details)
                .type(type)
                .build();

        IndividualEntity actualIndividualEntity = individualMapper.toIndividualEntity(expectedIndividual);

        assertThat(actualIndividualEntity).isNotNull();
        assertThat(actualIndividualEntity.getFirstName()).isEqualTo(firstName);
        assertThat(actualIndividualEntity.getLastName()).isEqualTo(lastName);
        assertThat(actualIndividualEntity.getDateOfBirth()).isEqualTo(dateOfBirth);
        assertThat(actualIndividualEntity.getIndividualContent()).isEqualTo(details);
        assertThat(actualIndividualEntity.getType()).isEqualTo(type);
    }
}
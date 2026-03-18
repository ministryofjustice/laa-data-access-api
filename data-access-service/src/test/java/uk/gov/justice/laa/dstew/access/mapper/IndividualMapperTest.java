package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.*;

class IndividualMapperTest {

    private final IndividualMapper individualMapper = Mappers.getMapper(IndividualMapper.class);

    @Test
    void givenNullIndividualEntity_whenToIndividual_thenReturnNull() {
        IndividualEntity entity = null;
        assertThat(individualMapper.toIndividual(entity)).isNull();
    }

    public static Stream<Arguments> getExtendedIndividualsData() {
      return Stream.of(
        Arguments.of("wilson", "additional", "prevref", "relchildren",
                List.of(
                    Map.of("k1", "v1"),
                    Map.of("k2", "v2")
                )
        )
      );
    }
    private ApplicationContent getExtendedIndividualApplicationContent(
            String lastName,
            String correspondenceAddressType,
            String previousApplicationReference,
            String relationshipToChildren,
            List<Map<String, Object>> addresses
    ) {
        return ApplicationContent.builder()
            .lastNameAtBirth(lastName)
            .correspondenceAddressType(correspondenceAddressType)
            .previousApplicationReference(previousApplicationReference)
            .relationshipToChildren(relationshipToChildren)
            .applicant(ApplicationApplicant.builder()
                .addresses(addresses)
                .build())
            .build();
    }

    @ParameterizedTest
    @MethodSource("getExtendedIndividualsData")
    void givenIndividualEntity_whenToExtendedIndividual_thenMapsFieldsCorrectly(
        String lastName,
        String correspondenceAddressType,
        String previousApplicationReference,
        String relationshipToChildren,
        List<Map<String, Object>> addresses
    ) {
        IndividualEntity expectedIndividualEntity = IndividualEntity.builder()
                .type(IndividualType.CLIENT)
                .build();

        Individual actualIndividual = individualMapper.toExtendedIndividual(
                expectedIndividualEntity,
                IndividualType.CLIENT,
                IncludedAdditionalData.CLIENT_DETAILS,
                getExtendedIndividualApplicationContent(lastName,
                                                        correspondenceAddressType,
                                                        previousApplicationReference,
                                                        relationshipToChildren,
                                                        addresses));

        assertThat(actualIndividual).isNotNull();
        assertThat(actualIndividual.getClientId()).isEqualTo(expectedIndividualEntity.getId());
        assertThat(actualIndividual.getCorrespondenceAddressType()).isEqualTo(correspondenceAddressType);
        assertThat(actualIndividual.getLastNameAtBirth()).isEqualTo(lastName);
        assertThat(actualIndividual.getPreviousApplicationReference()).isEqualTo(previousApplicationReference);
        assertThat(actualIndividual.getRelationshipToChildren()).isEqualTo(relationshipToChildren);
    }

    @ParameterizedTest
    @MethodSource("getExtendedIndividualsData")
    void givenIndividualEntity_whenToExtendedIndividualIncorrectly_thenMapsFieldsCorrectly(
            String lastName,
            String correspondenceAddressType,
            String previousApplicationReference,
            String relationshipToChildren,
            List<Map<String, Object>> addresses
    ) {
        IndividualEntity expectedIndividualEntity = IndividualEntity.builder()
                .type(IndividualType.CLIENT)
                .build();

        Individual actualIndividual = individualMapper.toExtendedIndividual(
                expectedIndividualEntity,
                IndividualType.CLIENT,
                null,
                getExtendedIndividualApplicationContent(lastName,
                        correspondenceAddressType,
                        previousApplicationReference,
                        relationshipToChildren,
                        addresses));

        assertThat(actualIndividual).isNotNull();
        assertThat(actualIndividual.getClientId()).isEqualTo(expectedIndividualEntity.getId());
        assertThat(actualIndividual.getCorrespondenceAddressType()).isNull();
        assertThat(actualIndividual.getLastNameAtBirth()).isNull();
        assertThat(actualIndividual.getPreviousApplicationReference()).isNull();
        assertThat(actualIndividual.getRelationshipToChildren()).isNull();
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
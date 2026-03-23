package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.Individual;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualGenerator;
import uk.gov.justice.laa.dstew.access.model.*;

@ExtendWith(MockitoExtension.class)
class IndividualMapperTest extends BaseMapperTest {

    @InjectMocks
    private IndividualMapperImpl individualMapper;

    @Test
    void givenNullIndividualEntity_whenToIndividual_thenReturnNull() {
        assertThat(individualMapper.toIndividual(null)).isNull();
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
        List<Map<String, Object>> actualAddresses = actualIndividual.getCorrespondenceAddress();
        assertThat(actualAddresses).hasSize(addresses.size());
        assertThat(actualAddresses.getFirst().get("k1").toString()).isEqualTo("v1");
        assertThat(actualAddresses.getLast().get("k2").toString()).isEqualTo("v2");
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
        assertThat(actualIndividual.getClientId()).isNull();
        assertThat(actualIndividual.getCorrespondenceAddressType()).isNull();
        assertThat(actualIndividual.getLastNameAtBirth()).isNull();
        assertThat(actualIndividual.getPreviousApplicationReference()).isNull();
        assertThat(actualIndividual.getRelationshipToChildren()).isNull();
        assertThat(actualIndividual.getCorrespondenceAddress()).isNull();
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
        assertThat(individualMapper.toIndividualEntity((Individual) null)).isNull();
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
package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallindividuals;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.domain.ApplicationClientDetailsDomain;
import uk.gov.justice.laa.dstew.access.domain.IndividualDomain;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationApplicant;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationContent;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;

class GetAllIndividualsGatewayMapperTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final GetAllIndividualsGatewayMapper mapper =
      new GetAllIndividualsGatewayMapper(objectMapper);

  @Test
  void givenFullyPopulatedEntity_whenToDomain_thenAllFieldsMapped() {
    UUID id = UUID.randomUUID();
    IndividualEntity entity =
        DataGenerator.createDefault(IndividualEntityGenerator.class, builder -> builder.id(id));

    IndividualDomain result = mapper.toDomain(entity);

    assertThat(result.id()).isEqualTo(id);
    assertThat(result.firstName()).isEqualTo(entity.getFirstName());
    assertThat(result.lastName()).isEqualTo(entity.getLastName());
    assertThat(result.dateOfBirth()).isEqualTo(entity.getDateOfBirth());
    assertThat(result.individualContent()).isEqualTo(entity.getIndividualContent());
    assertThat(result.type()).isEqualTo(IndividualType.CLIENT.name());
  }

  @Test
  void givenNullType_whenToDomain_thenTypeIsNull() {
    IndividualEntity entity =
        DataGenerator.createDefault(IndividualEntityGenerator.class, builder -> builder.type(null));

    IndividualDomain result = mapper.toDomain(entity);

    assertThat(result.type()).isNull();
  }

  @Test
  void givenFullyPopulatedApplicationContent_whenToClientDetails_thenAllFieldsMapped() {
    List<Map<String, Object>> addresses =
        List.of(Map.of("line1", "1 Main St"), Map.of("line1", "City"));
    ApplicationContent content =
        ApplicationContent.builder()
            .id(UUID.randomUUID())
            .submittedAt("2024-01-01T12:00:00Z")
            .lastNameAtBirth("Alberts")
            .previousApplicationId("ZZ999Z")
            .correspondenceAddressType("Home")
            .applicant(
                ApplicationApplicant.builder()
                    .appliedPreviously(true)
                    .addresses(addresses)
                    .relationshipToInvolvedChildren("Mother")
                    .build())
            .build();
    ApplicationEntity entity = buildEntityWithContent(content);

    ApplicationClientDetailsDomain result = mapper.toClientDetails(entity);

    ApplicationClientDetailsDomain expected =
        ApplicationClientDetailsDomain.builder()
            .lastNameAtBirth("Alberts")
            .previousApplicationId("ZZ999Z")
            .correspondenceAddressType("Home")
            .relationshipToInvolvedChildren("Mother")
            .appliedPreviously(true)
            .correspondenceAddress(addresses)
            .build();
    assertThat(result).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void givenNullApplicant_whenToClientDetails_thenApplicantSourcedFieldsAreNull() {
    ApplicationContent content =
        ApplicationContent.builder()
            .id(UUID.randomUUID())
            .submittedAt("2024-01-01T12:00:00Z")
            .lastNameAtBirth("Alberts")
            .previousApplicationId("ZZ999Z")
            .correspondenceAddressType("Home")
            .applicant(null)
            .build();
    ApplicationEntity entity = buildEntityWithContent(content);

    ApplicationClientDetailsDomain result = mapper.toClientDetails(entity);

    assertThat(result.lastNameAtBirth()).isEqualTo("Alberts");
    assertThat(result.previousApplicationId()).isEqualTo("ZZ999Z");
    assertThat(result.correspondenceAddressType()).isEqualTo("Home");
    assertThat(result.relationshipToInvolvedChildren()).isNull();
    assertThat(result.appliedPreviously()).isNull();
    assertThat(result.correspondenceAddress()).isNull();
  }

  @Test
  void givenNullAppliedPreviously_whenToClientDetails_thenAppliedPreviouslyIsNull() {
    ApplicationContent content =
        ApplicationContent.builder()
            .id(UUID.randomUUID())
            .submittedAt("2024-01-01T12:00:00Z")
            .applicant(
                ApplicationApplicant.builder()
                    .appliedPreviously(null)
                    .relationshipToInvolvedChildren("Mother")
                    .build())
            .build();
    ApplicationEntity entity = buildEntityWithContent(content);

    ApplicationClientDetailsDomain result = mapper.toClientDetails(entity);

    assertThat(result.appliedPreviously()).isNull();
    assertThat(result.relationshipToInvolvedChildren()).isEqualTo("Mother");
  }

  @Test
  void givenNonEmptyAddresses_whenToClientDetails_thenCorrespondenceAddressMapped() {
    List<Map<String, Object>> addresses =
        List.of(Map.of("line1", "address 1"), Map.of("line1", "address 2"));
    ApplicationContent content =
        ApplicationContent.builder()
            .id(UUID.randomUUID())
            .submittedAt("2024-01-01T12:00:00Z")
            .applicant(ApplicationApplicant.builder().addresses(addresses).build())
            .build();
    ApplicationEntity entity = buildEntityWithContent(content);

    ApplicationClientDetailsDomain result = mapper.toClientDetails(entity);

    assertThat(result.correspondenceAddress()).hasSize(2);
    assertThat(result.correspondenceAddress()).isEqualTo(addresses);
  }

  @Test
  void givenNullApplicationContent_whenToClientDetails_thenAllFieldsAreNull() {
    ApplicationEntity entity =
        ApplicationEntity.builder().id(UUID.randomUUID()).applicationContent(null).build();

    ApplicationClientDetailsDomain result = mapper.toClientDetails(entity);

    assertThat(result.lastNameAtBirth()).isNull();
    assertThat(result.previousApplicationId()).isNull();
    assertThat(result.correspondenceAddressType()).isNull();
    assertThat(result.relationshipToInvolvedChildren()).isNull();
    assertThat(result.appliedPreviously()).isNull();
    assertThat(result.correspondenceAddress()).isNull();
  }

  private ApplicationEntity buildEntityWithContent(ApplicationContent content) {
    Map<String, Object> contentMap = objectMapper.convertValue(content, Map.class);
    return ApplicationEntity.builder()
        .id(UUID.randomUUID())
        .applicationContent(new HashMap<>(contentMap))
        .build();
  }
}

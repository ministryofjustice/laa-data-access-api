package uk.gov.justice.laa.dstew.access.usecase.createapplication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.IndividualDomain;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.domain.enums.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.domain.enums.MatterType;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.Proceeding;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.createapplication.CreateApplicationCommandGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.createapplication.IndividualCommandGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingGenerator;

class CreateApplicationDomainMapperTest {

  private final CreateApplicationDomainMapper mapper = new CreateApplicationDomainMapper();

  // --- toApplicationDomain ---

  @Test
  void givenCommandAndParsedDetails_whenToApplicationDomain_thenMapsAllFieldsCorrectly() {
    CreateApplicationCommand command =
        DataGenerator.createDefault(CreateApplicationCommandGenerator.class);
    UUID applyApplicationId = UUID.randomUUID();
    Instant submittedAt = Instant.parse("2024-06-01T10:00:00Z");
    Proceeding proceeding = DataGenerator.createDefault(ProceedingGenerator.class);
    ParsedAppContentDetails parsed =
        ParsedAppContentDetails.builder()
            .applyApplicationId(applyApplicationId)
            .categoryOfLaw(CategoryOfLaw.FAMILY)
            .matterType(MatterType.SPECIAL_CHILDREN_ACT)
            .submittedAt(submittedAt)
            .officeCode("OFFICE001")
            .usedDelegatedFunctions(true)
            .proceedings(List.of(proceeding))
            .build();

    ApplicationDomain domain = mapper.toApplicationDomain(command, parsed);

    assertThat(domain.id()).isNull();
    assertThat(domain.createdAt()).isNull();
    assertThat(domain.status()).isEqualTo(command.status());
    assertThat(domain.laaReference()).isEqualTo(command.laaReference());
    assertThat(domain.applicationContent()).isEqualTo(command.applicationContent());
    assertThat(domain.schemaVersion())
        .isEqualTo(CreateApplicationDomainMapper.APPLICATION_SCHEMA_VERSION);
    assertThat(domain.applyApplicationId()).isEqualTo(applyApplicationId);
    assertThat(domain.categoryOfLaw()).isEqualTo("FAMILY");
    assertThat(domain.matterType()).isEqualTo("SPECIAL_CHILDREN_ACT");
    assertThat(domain.submittedAt()).isEqualTo(submittedAt);
    assertThat(domain.officeCode()).isEqualTo("OFFICE001");
    assertThat(domain.usedDelegatedFunctions()).isTrue();
    assertThat(domain.individuals()).hasSize(command.individuals().size());
    assertThat(domain.proceedings()).hasSize(1);
  }

  @Test
  void givenNullCategoryOfLaw_whenToApplicationDomain_thenCategoryOfLawIsNull() {
    CreateApplicationCommand command =
        DataGenerator.createDefault(CreateApplicationCommandGenerator.class);
    ParsedAppContentDetails parsed =
        ParsedAppContentDetails.builder()
            .applyApplicationId(UUID.randomUUID())
            .categoryOfLaw(null)
            .matterType(MatterType.SPECIAL_CHILDREN_ACT)
            .submittedAt(Instant.now())
            .proceedings(List.of(DataGenerator.createDefault(ProceedingGenerator.class)))
            .build();

    ApplicationDomain domain = mapper.toApplicationDomain(command, parsed);

    assertThat(domain.categoryOfLaw()).isNull();
  }

  @Test
  void givenNullMatterType_whenToApplicationDomain_thenMatterTypeIsNull() {
    CreateApplicationCommand command =
        DataGenerator.createDefault(CreateApplicationCommandGenerator.class);
    ParsedAppContentDetails parsed =
        ParsedAppContentDetails.builder()
            .applyApplicationId(UUID.randomUUID())
            .categoryOfLaw(CategoryOfLaw.FAMILY)
            .matterType(null)
            .submittedAt(Instant.now())
            .proceedings(List.of(DataGenerator.createDefault(ProceedingGenerator.class)))
            .build();

    ApplicationDomain domain = mapper.toApplicationDomain(command, parsed);

    assertThat(domain.matterType()).isNull();
  }

  @Test
  void givenNullProceedings_whenToApplicationDomain_thenProceedingsIsEmpty() {
    CreateApplicationCommand command =
        DataGenerator.createDefault(CreateApplicationCommandGenerator.class);
    ParsedAppContentDetails parsed =
        ParsedAppContentDetails.builder()
            .applyApplicationId(UUID.randomUUID())
            .submittedAt(Instant.now())
            .proceedings(null)
            .build();

    ApplicationDomain domain = mapper.toApplicationDomain(command, parsed);

    assertThat(domain.proceedings()).isEmpty();
  }

  @Test
  void givenNullIndividuals_whenToApplicationDomain_thenIndividualsIsEmpty() {
    CreateApplicationCommand command =
        DataGenerator.createDefault(
            CreateApplicationCommandGenerator.class, b -> b.individuals(null));
    ParsedAppContentDetails parsed =
        ParsedAppContentDetails.builder()
            .applyApplicationId(UUID.randomUUID())
            .submittedAt(Instant.now())
            .proceedings(List.of(DataGenerator.createDefault(ProceedingGenerator.class)))
            .build();

    ApplicationDomain domain = mapper.toApplicationDomain(command, parsed);

    assertThat(domain.individuals()).isEmpty();
  }

  // --- toIndividualDomains ---

  @Test
  void givenNullIndividualCommands_whenToIndividualDomains_thenReturnsEmptySet() {
    Set<IndividualDomain> result = mapper.toIndividualDomains(null);

    assertThat(result).isEmpty();
  }

  @Test
  void givenIndividualCommands_whenToIndividualDomains_thenReturnsMappedSet() {
    IndividualCommand command1 = DataGenerator.createDefault(IndividualCommandGenerator.class);
    IndividualCommand command2 =
        DataGenerator.createDefault(
            IndividualCommandGenerator.class, b -> b.firstName("Jane").lastName("Smith"));

    Set<IndividualDomain> result = mapper.toIndividualDomains(List.of(command1, command2));

    assertThat(result).hasSize(2);
  }

  // --- toIndividualDomain ---

  @Test
  void givenIndividualCommand_whenToIndividualDomain_thenMapsAllFieldsCorrectly() {
    IndividualCommand command = DataGenerator.createDefault(IndividualCommandGenerator.class);

    IndividualDomain domain = mapper.toIndividualDomain(command);

    assertThat(domain.firstName()).isEqualTo(command.firstName());
    assertThat(domain.lastName()).isEqualTo(command.lastName());
    assertThat(domain.dateOfBirth()).isEqualTo(command.dateOfBirth());
    assertThat(domain.individualContent()).isEqualTo(command.individualContent());
    assertThat(domain.type()).isEqualTo(command.type());
  }

  // --- toProceedingDomains ---

  @Test
  void givenNullProceedingList_whenToProceedingDomains_thenReturnsEmptySet() {
    assertThat(mapper.toProceedingDomains(null)).isEmpty();
  }

  @Test
  void givenProceedingList_whenToProceedingDomains_thenReturnsMappedSet() {
    Proceeding proceeding1 = DataGenerator.createDefault(ProceedingGenerator.class);
    Proceeding proceeding2 = DataGenerator.createDefault(ProceedingGenerator.class);

    Set<ProceedingDomain> result = mapper.toProceedingDomains(List.of(proceeding1, proceeding2));

    assertThat(result).hasSize(2);
  }

  @Test
  void givenProceedingListWithNullEntry_whenToProceedingDomains_thenNullEntryIsSkipped() {
    Proceeding proceeding = DataGenerator.createDefault(ProceedingGenerator.class);

    Set<ProceedingDomain> result = mapper.toProceedingDomains(List.of(proceeding));

    assertThat(result).hasSize(1);
  }

  // --- toProceedingDomain ---

  @Test
  void givenProceeding_whenToProceedingDomain_thenMapsAllFieldsCorrectly() {
    Proceeding proceeding = DataGenerator.createDefault(ProceedingGenerator.class);

    ProceedingDomain domain = mapper.toProceedingDomain(proceeding);

    assertThat(domain.applyProceedingId()).isEqualTo(proceeding.getId());
    assertThat(domain.isLead()).isEqualTo(Boolean.TRUE.equals(proceeding.getLeadProceeding()));
    assertThat(domain.description()).isEqualTo(proceeding.getDescription());
    assertThat(domain.proceedingContent()).isNotNull();
    assertThat(domain.createdBy()).isEmpty();
    assertThat(domain.updatedBy()).isEmpty();
  }

  @Test
  void givenNonLeadProceeding_whenToProceedingDomain_thenIsLeadIsFalse() {
    Proceeding proceeding =
        DataGenerator.createDefault(ProceedingGenerator.class, b -> b.leadProceeding(false));

    ProceedingDomain domain = mapper.toProceedingDomain(proceeding);

    assertThat(domain.isLead()).isFalse();
  }

  @Test
  void givenNullLeadProceeding_whenToProceedingDomain_thenIsLeadIsFalse() {
    Proceeding proceeding =
        DataGenerator.createDefault(ProceedingGenerator.class, b -> b.leadProceeding(null));

    ProceedingDomain domain = mapper.toProceedingDomain(proceeding);

    assertThat(domain.isLead()).isFalse();
  }

  // --- toProceedingContentMap ---

  @Test
  void givenProceeding_whenToProceedingContentMap_thenReturnsNonNullMap() {
    Proceeding proceeding = DataGenerator.createDefault(ProceedingGenerator.class);

    Map<String, Object> contentMap = mapper.toProceedingContentMap(proceeding);

    assertThat(contentMap).isNotNull().containsKey("id");
  }
}

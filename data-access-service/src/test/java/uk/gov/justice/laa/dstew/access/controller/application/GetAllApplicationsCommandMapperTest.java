package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.model.ApplicationOrderBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationSortBy;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.GetAllApplicationsCommand;

class GetAllApplicationsCommandMapperTest {

  private GetAllApplicationsCommandMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new GetAllApplicationsCommandMapper();
  }

  @Test
  void givenFullyPopulatedParams_whenToGetAllApplicationsCommand_thenAllFieldsMapped() {
    UUID userId = UUID.randomUUID();
    LocalDate dob = LocalDate.of(1990, 1, 1);

    GetAllApplicationsCommand command =
        mapper.toGetAllApplicationsCommand(
            ApplicationStatus.APPLICATION_SUBMITTED,
            "REF123",
            "Jane",
            "Doe",
            dob,
            userId,
            true,
            MatterType.SPECIAL_CHILDREN_ACT,
            ApplicationSortBy.SUBMITTED_DATE,
            ApplicationOrderBy.ASC,
            1,
            10);

    GetAllApplicationsCommand expected =
        GetAllApplicationsCommand.builder()
            .status("APPLICATION_SUBMITTED")
            .laaReference("REF123")
            .clientFirstName("Jane")
            .clientLastName("Doe")
            .clientDateOfBirth(dob)
            .userId(userId)
            .isAutoGranted(true)
            .matterType("SPECIAL_CHILDREN_ACT")
            .sortBy("SUBMITTED_DATE")
            .orderBy("ASC")
            .page(1)
            .pageSize(10)
            .build();

    assertThat(command).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void givenNullStatus_whenToGetAllApplicationsCommand_thenStatusIsNull() {
    GetAllApplicationsCommand command =
        mapper.toGetAllApplicationsCommand(
            null,
            "REF",
            "A",
            "B",
            LocalDate.now(),
            UUID.randomUUID(),
            true,
            MatterType.SPECIAL_CHILDREN_ACT,
            ApplicationSortBy.SUBMITTED_DATE,
            ApplicationOrderBy.ASC,
            1,
            10);
    assertThat(command.status()).isNull();
  }

  @Test
  void givenNullLaaReference_whenToGetAllApplicationsCommand_thenLaaReferenceIsNull() {
    GetAllApplicationsCommand command =
        mapper.toGetAllApplicationsCommand(
            ApplicationStatus.APPLICATION_SUBMITTED,
            null,
            "A",
            "B",
            LocalDate.now(),
            UUID.randomUUID(),
            true,
            MatterType.SPECIAL_CHILDREN_ACT,
            ApplicationSortBy.SUBMITTED_DATE,
            ApplicationOrderBy.ASC,
            1,
            10);
    assertThat(command.laaReference()).isNull();
  }

  @Test
  void givenNullClientFirstName_whenToGetAllApplicationsCommand_thenClientFirstNameIsNull() {
    GetAllApplicationsCommand command =
        mapper.toGetAllApplicationsCommand(
            ApplicationStatus.APPLICATION_SUBMITTED,
            "REF",
            null,
            "B",
            LocalDate.now(),
            UUID.randomUUID(),
            true,
            MatterType.SPECIAL_CHILDREN_ACT,
            ApplicationSortBy.SUBMITTED_DATE,
            ApplicationOrderBy.ASC,
            1,
            10);
    assertThat(command.clientFirstName()).isNull();
  }

  @Test
  void givenNullClientLastName_whenToGetAllApplicationsCommand_thenClientLastNameIsNull() {
    GetAllApplicationsCommand command =
        mapper.toGetAllApplicationsCommand(
            ApplicationStatus.APPLICATION_SUBMITTED,
            "REF",
            "A",
            null,
            LocalDate.now(),
            UUID.randomUUID(),
            true,
            MatterType.SPECIAL_CHILDREN_ACT,
            ApplicationSortBy.SUBMITTED_DATE,
            ApplicationOrderBy.ASC,
            1,
            10);
    assertThat(command.clientLastName()).isNull();
  }

  @Test
  void givenNullClientDateOfBirth_whenToGetAllApplicationsCommand_thenClientDateOfBirthIsNull() {
    GetAllApplicationsCommand command =
        mapper.toGetAllApplicationsCommand(
            ApplicationStatus.APPLICATION_SUBMITTED,
            "REF",
            "A",
            "B",
            null,
            UUID.randomUUID(),
            true,
            MatterType.SPECIAL_CHILDREN_ACT,
            ApplicationSortBy.SUBMITTED_DATE,
            ApplicationOrderBy.ASC,
            1,
            10);
    assertThat(command.clientDateOfBirth()).isNull();
  }

  @Test
  void givenNullUserId_whenToGetAllApplicationsCommand_thenUserIdIsNull() {
    GetAllApplicationsCommand command =
        mapper.toGetAllApplicationsCommand(
            ApplicationStatus.APPLICATION_SUBMITTED,
            "REF",
            "A",
            "B",
            LocalDate.now(),
            null,
            true,
            MatterType.SPECIAL_CHILDREN_ACT,
            ApplicationSortBy.SUBMITTED_DATE,
            ApplicationOrderBy.ASC,
            1,
            10);
    assertThat(command.userId()).isNull();
  }

  @Test
  void givenNullIsAutoGranted_whenToGetAllApplicationsCommand_thenIsAutoGrantedIsNull() {
    GetAllApplicationsCommand command =
        mapper.toGetAllApplicationsCommand(
            ApplicationStatus.APPLICATION_SUBMITTED,
            "REF",
            "A",
            "B",
            LocalDate.now(),
            UUID.randomUUID(),
            null,
            MatterType.SPECIAL_CHILDREN_ACT,
            ApplicationSortBy.SUBMITTED_DATE,
            ApplicationOrderBy.ASC,
            1,
            10);
    assertThat(command.isAutoGranted()).isNull();
  }

  @Test
  void givenNullMatterType_whenToGetAllApplicationsCommand_thenMatterTypeIsNull() {
    GetAllApplicationsCommand command =
        mapper.toGetAllApplicationsCommand(
            ApplicationStatus.APPLICATION_SUBMITTED,
            "REF",
            "A",
            "B",
            LocalDate.now(),
            UUID.randomUUID(),
            true,
            null,
            ApplicationSortBy.SUBMITTED_DATE,
            ApplicationOrderBy.ASC,
            1,
            10);
    assertThat(command.matterType()).isNull();
  }

  @Test
  void givenNullSortBy_whenToGetAllApplicationsCommand_thenSortByIsNull() {
    GetAllApplicationsCommand command =
        mapper.toGetAllApplicationsCommand(
            ApplicationStatus.APPLICATION_SUBMITTED,
            "REF",
            "A",
            "B",
            LocalDate.now(),
            UUID.randomUUID(),
            true,
            MatterType.SPECIAL_CHILDREN_ACT,
            null,
            ApplicationOrderBy.ASC,
            1,
            10);
    assertThat(command.sortBy()).isNull();
  }

  @Test
  void givenNullOrderBy_whenToGetAllApplicationsCommand_thenOrderByIsNull() {
    GetAllApplicationsCommand command =
        mapper.toGetAllApplicationsCommand(
            ApplicationStatus.APPLICATION_SUBMITTED,
            "REF",
            "A",
            "B",
            LocalDate.now(),
            UUID.randomUUID(),
            true,
            MatterType.SPECIAL_CHILDREN_ACT,
            ApplicationSortBy.SUBMITTED_DATE,
            null,
            1,
            10);
    assertThat(command.orderBy()).isNull();
  }

  @Test
  void givenNullPage_whenToGetAllApplicationsCommand_thenPageIsNull() {
    GetAllApplicationsCommand command =
        mapper.toGetAllApplicationsCommand(
            ApplicationStatus.APPLICATION_SUBMITTED,
            "REF",
            "A",
            "B",
            LocalDate.now(),
            UUID.randomUUID(),
            true,
            MatterType.SPECIAL_CHILDREN_ACT,
            ApplicationSortBy.SUBMITTED_DATE,
            ApplicationOrderBy.ASC,
            null,
            10);
    assertThat(command.page()).isNull();
  }

  @Test
  void givenNullPageSize_whenToGetAllApplicationsCommand_thenPageSizeIsNull() {
    GetAllApplicationsCommand command =
        mapper.toGetAllApplicationsCommand(
            ApplicationStatus.APPLICATION_SUBMITTED,
            "REF",
            "A",
            "B",
            LocalDate.now(),
            UUID.randomUUID(),
            true,
            MatterType.SPECIAL_CHILDREN_ACT,
            ApplicationSortBy.SUBMITTED_DATE,
            ApplicationOrderBy.ASC,
            1,
            null);
    assertThat(command.pageSize()).isNull();
  }
}

package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getallapplications;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.domain.ApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.domain.LinkedApplicationSummaryDomain;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.IndividualSummaryDto;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationSummaryDtoGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.LinkedApplicationSummaryDtoGenerator;

class GetAllApplicationsGatewayMapperTest {

  private GetAllApplicationsGatewayMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new GetAllApplicationsGatewayMapper();
  }

  @Test
  void givenFullyPopulatedDto_whenToApplicationSummaryDomain_thenAllFieldsMapped() {
    UUID id = UUID.randomUUID();
    Instant submittedAt = Instant.parse("2024-01-01T10:00:00Z");
    Instant modifiedAt = Instant.parse("2024-06-01T12:00:00Z");
    UUID caseworkerId = UUID.randomUUID();
    IndividualSummaryDto client =
        IndividualSummaryDto.builder()
            .type(IndividualType.CLIENT)
            .firstName("Jane")
            .lastName("Doe")
            .dateOfBirth(LocalDate.of(1990, 5, 15))
            .build();

    ApplicationSummaryDto dto =
        DataGenerator.createDefault(
            ApplicationSummaryDtoGenerator.class,
            builder ->
                builder
                    .id(id)
                    .submittedAt(submittedAt)
                    .isAutoGranted(true)
                    .categoryOfLaw(CategoryOfLaw.FAMILY)
                    .matterType(MatterType.SPECIAL_CHILDREN_ACT)
                    .usedDelegatedFunctions(true)
                    .laaReference("REF123")
                    .officeCode("1A234B")
                    .status(ApplicationStatus.APPLICATION_SUBMITTED)
                    .caseworkerId(caseworkerId)
                    .modifiedAt(modifiedAt)
                    .isLead(true)
                    .individuals(List.of(client)));

    ApplicationSummaryDomain domain = mapper.toApplicationSummaryDomain(dto);

    ApplicationSummaryDomain expected =
        ApplicationSummaryDomain.builder()
            .id(id)
            .submittedAt(submittedAt)
            .isAutoGranted(true)
            .categoryOfLaw("FAMILY")
            .matterType("SPECIAL_CHILDREN_ACT")
            .usedDelegatedFunctions(true)
            .laaReference("REF123")
            .officeCode("1A234B")
            .status("APPLICATION_SUBMITTED")
            .caseworkerId(caseworkerId)
            .clientFirstName("Jane")
            .clientLastName("Doe")
            .clientDateOfBirth(LocalDate.of(1990, 5, 15))
            .applicationType("INITIAL")
            .modifiedAt(modifiedAt)
            .isLead(true)
            .linkedApplications(List.of())
            .build();

    assertThat(domain).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void givenNullSubmittedAt_whenToApplicationSummaryDomain_thenSubmittedAtIsNull() {
    ApplicationSummaryDto dto =
        DataGenerator.createDefault(
            ApplicationSummaryDtoGenerator.class, builder -> builder.submittedAt(null));

    ApplicationSummaryDomain domain = mapper.toApplicationSummaryDomain(dto);

    assertThat(domain.submittedAt()).isNull();
  }

  @Test
  void givenNullCaseworkerId_whenToApplicationSummaryDomain_thenCaseworkerIdIsNull() {
    ApplicationSummaryDto dto =
        DataGenerator.createDefault(
            ApplicationSummaryDtoGenerator.class, builder -> builder.caseworkerId(null));

    ApplicationSummaryDomain domain = mapper.toApplicationSummaryDomain(dto);

    assertThat(domain.caseworkerId()).isNull();
  }

  @Test
  void givenNoClientIndividual_whenToApplicationSummaryDomain_thenClientFieldsAreNull() {
    ApplicationSummaryDto dto =
        DataGenerator.createDefault(
            ApplicationSummaryDtoGenerator.class, builder -> builder.individuals(List.of()));

    ApplicationSummaryDomain domain = mapper.toApplicationSummaryDomain(dto);

    assertThat(domain.clientFirstName()).isNull();
    assertThat(domain.clientLastName()).isNull();
    assertThat(domain.clientDateOfBirth()).isNull();
  }

  @Test
  void givenNullDto_whenToApplicationSummaryDomain_thenReturnsNull() {
    assertThat(mapper.toApplicationSummaryDomain(null)).isNull();
  }

  @Test
  void givenFullyPopulatedLinkedDto_whenToLinkedApplicationSummaryDomain_thenAllFieldsMapped() {
    UUID applicationId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();

    LinkedApplicationSummaryDto dto =
        DataGenerator.createDefault(
            LinkedApplicationSummaryDtoGenerator.class,
            builder ->
                builder
                    .applicationId(applicationId)
                    .laaReference("REF456")
                    .isLead(true)
                    .leadApplicationId(leadApplicationId));

    LinkedApplicationSummaryDomain domain = mapper.toLinkedApplicationSummaryDomain(dto);

    LinkedApplicationSummaryDomain expected =
        LinkedApplicationSummaryDomain.builder()
            .applicationId(applicationId)
            .laaReference("REF456")
            .isLead(true)
            .leadApplicationId(leadApplicationId)
            .build();

    assertThat(domain).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void givenNullLinkedDto_whenToLinkedApplicationSummaryDomain_thenReturnsNull() {
    assertThat(mapper.toLinkedApplicationSummaryDomain(null)).isNull();
  }

  @Test
  void givenNullStatus_whenToApplicationSummaryDomain_thenStatusIsNull() {
    ApplicationSummaryDto dto =
        DataGenerator.createDefault(
            ApplicationSummaryDtoGenerator.class, builder -> builder.status(null));

    ApplicationSummaryDomain domain = mapper.toApplicationSummaryDomain(dto);

    assertThat(domain.status()).isNull();
  }

  @Test
  void givenNullIndividualsList_whenToApplicationSummaryDomain_thenClientFieldsAreNull() {
    ApplicationSummaryDto dto =
        DataGenerator.createDefault(
            ApplicationSummaryDtoGenerator.class, builder -> builder.individuals(null));

    ApplicationSummaryDomain domain = mapper.toApplicationSummaryDomain(dto);

    assertThat(domain.clientFirstName()).isNull();
    assertThat(domain.clientLastName()).isNull();
    assertThat(domain.clientDateOfBirth()).isNull();
  }
}

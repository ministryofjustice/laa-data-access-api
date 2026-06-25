package uk.gov.justice.laa.dstew.access.usecase.getapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.dto.ApplicationDbProjection;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.dto.ProceedingDbProjection;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.InvolvedChild;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.OpponentDetails;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.Opposable;

class GetApplicationReadModelMapperTest {

  private GetApplicationReadModelMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new GetApplicationReadModelMapper();
  }

  @Test
  void givenFullyPopulatedProjection_whenMapped_thenAllFieldsMapped() {
    UUID proceedingId = UUID.randomUUID();

    InvolvedChild child =
        InvolvedChild.builder()
            .id(UUID.randomUUID())
            .fullName("John Smith")
            .dateOfBirth(LocalDate.of(2020, 1, 1))
            .build();

    ProceedingDbProjection proceeding =
        ProceedingDbProjection.builder()
            .proceedingId(proceedingId)
            .description("Proceeding description")
            .meritsDecision("GRANTED")
            .proceedingType("hearing")
            .categoryOfLaw("Family")
            .matterType("SPECIAL_CHILDREN_ACT")
            .levelOfService("SERVICE")
            .substantiveCostLimitation(1234.56)
            .delegatedFunctionsDate(LocalDate.of(2025, 5, 6))
            .scopeLimitations(
                List.of(Map.of("meaning", "Full Representation", "description", "desc")))
            .involvedChildren(List.of(child))
            .build();

    ApplicationDbProjection projection =
        ApplicationDbProjection.builder()
            .id(UUID.randomUUID())
            .status("APPLICATION_IN_PROGRESS")
            .laaReference("REF123")
            .updatedAt(Instant.parse("2024-06-01T10:00:00Z"))
            .caseworkerId(UUID.randomUUID())
            .submittedAt(Instant.parse("2024-01-01T12:00:00Z"))
            .isLead(true)
            .usedDelegatedFunctions(false)
            .autoGrant(true)
            .decisionStatus("GRANTED")
            .version(1L)
            .officeCode("OFFICE1")
            .submitterEmail("contact@example.com")
            .opponents(
                List.of(
                    OpponentDetails.builder()
                        .opposableType("Individual")
                        .opposable(
                            Opposable.builder()
                                .firstName("Jane")
                                .lastName("Doe")
                                .name(null)
                                .build())
                        .build()))
            .proceedings(List.of(proceeding))
            .build();

    ApplicationReadModel actual = mapper.toApplicationReadModel(projection, "INITIAL");

    // Scalar fields
    assertThat(actual.id()).isEqualTo(projection.id());
    assertThat(actual.status()).isEqualTo(projection.status());
    assertThat(actual.laaReference()).isEqualTo(projection.laaReference());
    assertThat(actual.updatedAt()).isEqualTo(projection.updatedAt());
    assertThat(actual.caseworkerId()).isEqualTo(projection.caseworkerId());
    assertThat(actual.submittedAt()).isEqualTo(projection.submittedAt());
    assertThat(actual.isLead()).isEqualTo(projection.isLead());
    assertThat(actual.usedDelegatedFunctions()).isEqualTo(projection.usedDelegatedFunctions());
    assertThat(actual.autoGrant()).isEqualTo(projection.autoGrant());
    assertThat(actual.decisionStatus()).isEqualTo(projection.decisionStatus());
    assertThat(actual.version()).isEqualTo(projection.version());

    // Business rule: applicationType always INITIAL
    assertThat(actual.applicationType()).isEqualTo("INITIAL");

    // Provider
    assertThat(actual.provider()).isNotNull();
    assertThat(actual.provider().officeCode()).isEqualTo("OFFICE1");
    assertThat(actual.provider().contactEmail()).isEqualTo("contact@example.com");

    // Opponents mapped from OpponentDetails
    assertThat(actual.opponents()).hasSize(1);
    assertThat(actual.opponents().getFirst().opponentType()).isEqualTo("Individual");
    assertThat(actual.opponents().getFirst().firstName()).isEqualTo("Jane");
    assertThat(actual.opponents().getFirst().lastName()).isEqualTo("Doe");
    assertThat(actual.opponents().getFirst().organisationName()).isNull();

    // Proceedings with pre-resolved involved children from projection
    assertThat(actual.proceedings()).hasSize(1);
    assertThat(actual.proceedings().getFirst().proceedingId()).isEqualTo(proceedingId);
    assertThat(actual.proceedings().getFirst().description()).isEqualTo("Proceeding description");
    assertThat(actual.proceedings().getFirst().proceedingType()).isEqualTo("hearing");
    assertThat(actual.proceedings().getFirst().categoryOfLaw()).isEqualTo("Family");
    assertThat(actual.proceedings().getFirst().matterType()).isEqualTo("SPECIAL_CHILDREN_ACT");
    assertThat(actual.proceedings().getFirst().levelOfService()).isEqualTo("SERVICE");
    assertThat(actual.proceedings().getFirst().substantiveCostLimitation()).isEqualTo(1234.56);
    assertThat(actual.proceedings().getFirst().delegatedFunctionsDate())
        .isEqualTo(LocalDate.of(2025, 5, 6));
    assertThat(actual.proceedings().getFirst().meritsDecision()).isEqualTo("GRANTED");

    // Involved children mapped from proceeding projection
    assertThat(actual.proceedings().getFirst().involvedChildren()).hasSize(1);
    assertThat(actual.proceedings().getFirst().involvedChildren().getFirst().fullName())
        .isEqualTo("John Smith");
    assertThat(actual.proceedings().getFirst().involvedChildren().getFirst().dateOfBirth())
        .isEqualTo(LocalDate.of(2020, 1, 1));

    // Scope limitations mapped from raw map
    assertThat(actual.proceedings().getFirst().scopeLimitations()).hasSize(1);
    assertThat(actual.proceedings().getFirst().scopeLimitations().getFirst().scopeLimitation())
        .isEqualTo("Full Representation");
    assertThat(actual.proceedings().getFirst().scopeLimitations().getFirst().scopeDescription())
        .isEqualTo("desc");
  }

  @ParameterizedTest(name = "[{index}] officeCode={0}, submitterEmail={1}")
  @MethodSource("providerScenarios")
  void givenProviderFieldVariants_whenMapped_thenProviderIsMappedAsExpected(
      String officeCode,
      String submitterEmail,
      boolean providerExpected,
      String expectedOfficeCode,
      String expectedContactEmail) {
    ApplicationDbProjection projection =
        minimalProjection().toBuilder()
            .officeCode(officeCode)
            .submitterEmail(submitterEmail)
            .build();

    ApplicationReadModel actual = mapper.toApplicationReadModel(projection, "INITIAL");

    if (!providerExpected) {
      assertThat(actual.provider()).isNull();
    } else {
      assertThat(actual.provider()).isNotNull();
      assertThat(actual.provider().officeCode()).isEqualTo(expectedOfficeCode);
      assertThat(actual.provider().contactEmail()).isEqualTo(expectedContactEmail);
    }
  }

  @Test
  void givenAlwaysSetApplicationType_whenMapped_thenApplicationTypeIsInitial() {
    ApplicationReadModel actual = mapper.toApplicationReadModel(minimalProjection(), "INITIAL");

    assertThat(actual.applicationType()).isEqualTo("INITIAL");
  }

  @Test
  void givenNullInvolvedChildrenOnProceeding_whenMapped_thenInvolvedChildrenIsEmpty() {
    ProceedingDbProjection proceeding =
        ProceedingDbProjection.builder()
            .proceedingId(UUID.randomUUID())
            .involvedChildren(null)
            .build();

    ApplicationDbProjection projection =
        minimalProjection().toBuilder().proceedings(List.of(proceeding)).build();

    ApplicationReadModel actual = mapper.toApplicationReadModel(projection, "INITIAL");

    assertThat(actual.proceedings().getFirst().involvedChildren()).isEmpty();
  }

  @Test
  void givenNullProceedings_whenMapped_thenProceedingsIsEmpty() {
    ApplicationDbProjection projection = minimalProjection().toBuilder().proceedings(null).build();

    ApplicationReadModel actual = mapper.toApplicationReadModel(projection, "INITIAL");

    assertThat(actual.proceedings()).isEmpty();
  }

  @Test
  void givenNullOpponents_whenMapped_thenOpponentsIsEmpty() {
    ApplicationDbProjection projection = minimalProjection().toBuilder().opponents(null).build();

    ApplicationReadModel actual = mapper.toApplicationReadModel(projection, "INITIAL");

    assertThat(actual.opponents()).isEmpty();
  }

  @Test
  void givenOpponentWithNullOpposable_whenMapped_thenNameFieldsAreNull() {
    ApplicationDbProjection projection =
        minimalProjection().toBuilder()
            .opponents(
                List.of(
                    OpponentDetails.builder().opposableType("Individual").opposable(null).build()))
            .build();

    ApplicationReadModel actual = mapper.toApplicationReadModel(projection, "INITIAL");

    assertThat(actual.opponents()).hasSize(1);
    assertThat(actual.opponents().getFirst().firstName()).isNull();
    assertThat(actual.opponents().getFirst().lastName()).isNull();
    assertThat(actual.opponents().getFirst().organisationName()).isNull();
  }

  @Test
  void givenNullScopeLimitations_whenMapped_thenScopeLimitationsIsEmpty() {
    ProceedingDbProjection proceeding =
        ProceedingDbProjection.builder()
            .proceedingId(UUID.randomUUID())
            .scopeLimitations(null)
            .build();

    ApplicationDbProjection projection =
        minimalProjection().toBuilder().proceedings(List.of(proceeding)).build();

    ApplicationReadModel actual = mapper.toApplicationReadModel(projection, "INITIAL");

    assertThat(actual.proceedings().getFirst().scopeLimitations()).isEmpty();
  }

  @Test
  void givenScopeLimitationsWithMissingFields_whenMapped_thenMissingFieldsMapToNull() {
    ProceedingDbProjection proceeding =
        ProceedingDbProjection.builder()
            .proceedingId(UUID.randomUUID())
            .scopeLimitations(
                List.of(
                    Map.of("description", "only description"), Map.of("meaning", "only meaning")))
            .build();

    ApplicationDbProjection projection =
        minimalProjection().toBuilder().proceedings(List.of(proceeding)).build();

    ApplicationReadModel actual = mapper.toApplicationReadModel(projection, "INITIAL");

    assertThat(actual.proceedings().getFirst().scopeLimitations()).hasSize(2);
    assertThat(actual.proceedings().getFirst().scopeLimitations().get(0).scopeLimitation())
        .isNull();
    assertThat(actual.proceedings().getFirst().scopeLimitations().get(0).scopeDescription())
        .isEqualTo("only description");
    assertThat(actual.proceedings().getFirst().scopeLimitations().get(1).scopeLimitation())
        .isEqualTo("only meaning");
    assertThat(actual.proceedings().getFirst().scopeLimitations().get(1).scopeDescription())
        .isNull();
  }

  private ApplicationDbProjection minimalProjection() {
    return ApplicationDbProjection.builder()
        .id(UUID.randomUUID())
        .status("APPLICATION_IN_PROGRESS")
        .laaReference("REF123")
        .updatedAt(Instant.parse("2024-06-01T10:00:00Z"))
        .caseworkerId(UUID.randomUUID())
        .submittedAt(Instant.parse("2024-01-01T12:00:00Z"))
        .isLead(false)
        .usedDelegatedFunctions(false)
        .autoGrant(false)
        .decisionStatus("GRANTED")
        .version(0L)
        .officeCode("OFFICE1")
        .submitterEmail("test@example.com")
        .opponents(Collections.emptyList())
        .proceedings(Collections.emptyList())
        .build();
  }

  private static Stream<Arguments> providerScenarios() {
    return Stream.of(
        arguments(null, null, false, null, null),
        arguments(null, "x@y.z", true, null, "x@y.z"),
        arguments("OFFICE1", null, true, "OFFICE1", null));
  }
}

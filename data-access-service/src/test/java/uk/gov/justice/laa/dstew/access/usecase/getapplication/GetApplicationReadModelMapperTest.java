package uk.gov.justice.laa.dstew.access.usecase.getapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.math.BigDecimal;
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
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.mapper.ProceedingMapper;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.dto.ApplicationDbProjection;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.dto.ProceedingDbProjection;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.InvolvedChild;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.OpponentDetails;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.Opposable;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ProceedingLinkedChild;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ProceedingMerits;

class GetApplicationReadModelMapperTest {

  private GetApplicationReadModelMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new GetApplicationReadModelMapper();
  }

  @Test
  void givenLegacyLinkedChild_whenOldProceedingMapperUsed_thenReturnsNullSplitNames() {
    UUID childId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    var child = InvolvedChild.builder().id(childId).fullName("Mary Jane Smith").build();
    var entity =
        ProceedingEntity.builder()
            .applyProceedingId(proceedingId)
            .proceedingContent(Map.of())
            .build();
    var merits =
        ProceedingMerits.builder()
            .proceedingId(proceedingId)
            .proceedingLinkedChildren(
                List.of(ProceedingLinkedChild.builder().involvedChildId(childId).build()))
            .build();

    var response =
        new ProceedingMapper() {}.toApplicationProceeding(entity, List.of(merits), List.of(child))
            .getInvolvedChildren()
            .getFirst();

    assertThat(response.getFirstName()).isNull();
    assertThat(response.getLastName()).isNull();
    assertThat(child.getFullName()).isEqualTo("Mary Jane Smith");
    assertThat(child.getFirstName()).isNull();
  }

  @Test
  void givenHistoricalRawChild_whenDeserializedMappedAndSerialized_thenPreservesOriginalContent() {
    var jsonMapper = JsonMapper.builder().build();
    String json =
        """
                {"id":"00000000-0000-0000-0000-000000000123","fullName":"Mary Jane Smith",
                 "dateOfBirth":"2020-01-01","legacyExtra":"preserved"}
                """;
    InvolvedChild child = jsonMapper.readValue(json, InvolvedChild.class);
    var projection =
        minimalProjection().toBuilder()
            .proceedings(
                List.of(ProceedingDbProjection.builder().involvedChildren(List.of(child)).build()))
            .build();

    var response =
        mapper
            .toApplicationReadModel(projection)
            .proceedings()
            .getFirst()
            .involvedChildren()
            .getFirst();
    var stored = jsonMapper.readTree(jsonMapper.writeValueAsString(child));

    assertThat(response.firstName()).isNull();
    assertThat(response.lastName()).isNull();
    assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(2020, 1, 1));
    assertThat(stored.get("fullName").asString()).isEqualTo("Mary Jane Smith");
    assertThat(stored.get("legacyExtra").asString()).isEqualTo("preserved");
    assertThat(child.getFirstName()).isNull();
    assertThat(child.getLastName()).isNull();
  }

  @ParameterizedTest
  @MethodSource("legacyChildNames")
  void givenLegacyChild_whenMapped_thenUsesSplitFieldsAsStoredWithoutChangingContent(
      String fullName,
      String firstName,
      String lastName,
      String expectedFirst,
      String expectedLast) {
    InvolvedChild child =
        InvolvedChild.builder().fullName(fullName).firstName(firstName).lastName(lastName).build();
    var projection =
        minimalProjection().toBuilder()
            .proceedings(
                List.of(ProceedingDbProjection.builder().involvedChildren(List.of(child)).build()))
            .build();

    var response =
        mapper
            .toApplicationReadModel(projection)
            .proceedings()
            .getFirst()
            .involvedChildren()
            .getFirst();

    assertThat(response.firstName()).isEqualTo(expectedFirst);
    assertThat(response.lastName()).isEqualTo(expectedLast);
    assertThat(child.getFullName()).isEqualTo(fullName);
    assertThat(child.getFirstName()).isEqualTo(firstName);
    assertThat(child.getLastName()).isEqualTo(lastName);
  }

  static Stream<Arguments> legacyChildNames() {
    return Stream.of(
        arguments("Mary Jane Smith", null, null, null, null),
        arguments("  Mary Jane Smith  ", null, null, null, null),
        arguments("Mary  Jane Smith", null, null, null, null),
        arguments("Mary  Smith", null, null, null, null),
        arguments("Smith", null, null, null, null),
        arguments(" \t ", null, null, null, null),
        arguments(null, null, null, null, null),
        arguments("", null, null, null, null),
        arguments("Mary\tSmith", null, null, null, null),
        arguments("Mary van der Berg", null, null, null, null),
        arguments("Mary Jane Smith", "Explicit", null, "Explicit", null),
        arguments("Mary Jane Smith", null, "Explicit", null, "Explicit"),
        arguments("Mary Jane Smith", "", "", "", ""),
        arguments("Legacy Name", "Current", "Surname", "Current", "Surname"));
  }

  @Test
  void givenFullyPopulatedProjection_whenMapped_thenAllFieldsMapped() {
    UUID proceedingId = UUID.randomUUID();

    InvolvedChild child =
        InvolvedChild.builder()
            .id(UUID.randomUUID())
            .firstName("John")
            .lastName("Smith")
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
            .substantiveCostLimitation(new BigDecimal("1234.56"))
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

    ApplicationReadModel actual = mapper.toApplicationReadModel(projection);

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
    assertThat(actual.proceedings().getFirst().substantiveCostLimitation())
        .isEqualTo(new BigDecimal("1234.56"));
    assertThat(actual.proceedings().getFirst().delegatedFunctionsDate())
        .isEqualTo(LocalDate.of(2025, 5, 6));
    assertThat(actual.proceedings().getFirst().meritsDecision()).isEqualTo("GRANTED");

    // Involved children mapped from proceeding projection
    assertThat(actual.proceedings().getFirst().involvedChildren()).hasSize(1);
    assertThat(actual.proceedings().getFirst().involvedChildren().getFirst().firstName())
        .isEqualTo("John");
    assertThat(actual.proceedings().getFirst().involvedChildren().getFirst().lastName())
        .isEqualTo("Smith");
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

    ApplicationReadModel actual = mapper.toApplicationReadModel(projection);

    if (!providerExpected) {
      assertThat(actual.provider()).isNull();
    } else {
      assertThat(actual.provider()).isNotNull();
      assertThat(actual.provider().officeCode()).isEqualTo(expectedOfficeCode);
      assertThat(actual.provider().contactEmail()).isEqualTo(expectedContactEmail);
    }
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

    ApplicationReadModel actual = mapper.toApplicationReadModel(projection);

    assertThat(actual.proceedings().getFirst().involvedChildren()).isEmpty();
  }

  @Test
  void givenNullProceedings_whenMapped_thenProceedingsIsEmpty() {
    ApplicationDbProjection projection = minimalProjection().toBuilder().proceedings(null).build();

    ApplicationReadModel actual = mapper.toApplicationReadModel(projection);

    assertThat(actual.proceedings()).isEmpty();
  }

  @Test
  void givenNullOpponents_whenMapped_thenOpponentsIsEmpty() {
    ApplicationDbProjection projection = minimalProjection().toBuilder().opponents(null).build();

    ApplicationReadModel actual = mapper.toApplicationReadModel(projection);

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

    ApplicationReadModel actual = mapper.toApplicationReadModel(projection);

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

    ApplicationReadModel actual = mapper.toApplicationReadModel(projection);

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

    ApplicationReadModel actual = mapper.toApplicationReadModel(projection);

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

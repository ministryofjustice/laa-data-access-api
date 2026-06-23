package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getapplication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CaseworkerEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.OpponentDetails;
import uk.gov.justice.laa.dstew.access.model.ProceedingLinkedChild;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ApplicationProceedingReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.model.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationContent;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationMerits;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ProceedingMerits;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationContentGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationMeritsGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;

class GetApplicationGatewayMapperTest {

  private ObjectMapper objectMapper;
  private GetApplicationGatewayMapper mapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    mapper = new GetApplicationGatewayMapper(objectMapper);
  }

  @Test
  void givenFullyPopulatedEntity_whenMapped_thenAllFieldsAreMapped() {
    UUID applyProceedingId = UUID.randomUUID();
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    ApplicationContent applicationContent =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class,
            builder ->
                builder.proceedingMerits(
                    List.of(
                        ProceedingMerits.builder()
                            .proceedingId(applyProceedingId)
                            .proceedingLinkedChildren(
                                List.of(
                                    ProceedingLinkedChild.builder()
                                        .involvedChildId(
                                            ApplicationMeritsGenerator.DEFAULT_INVOLVED_CHILD_ID)
                                        .build()))
                            .build())));

    ApplicationEntity applicationEntity =
        newApplicationEntity(
            objectMapper.convertValue(applicationContent, Map.class), Set.of(proceedingEntity));

    ApplicationReadModel actual = mapper.toApplicationReadModel(applicationEntity);

    // Exhaustive scalar-field assertions (G9)
    assertThat(actual.id()).isEqualTo(applicationEntity.getId());
    assertThat(actual.status()).isEqualTo(applicationEntity.getStatus().name());
    assertThat(actual.laaReference()).isEqualTo(applicationEntity.getLaaReference());
    assertThat(actual.updatedAt()).isEqualTo(applicationEntity.getUpdatedAt());
    assertThat(actual.caseworkerId()).isEqualTo(applicationEntity.getCaseworker().getId());
    assertThat(actual.submittedAt()).isEqualTo(applicationEntity.getSubmittedAt());
    assertThat(actual.isLead()).isEqualTo(applicationEntity.isLead());
    assertThat(actual.usedDelegatedFunctions())
        .isEqualTo(applicationEntity.getUsedDelegatedFunctions());
    assertThat(actual.autoGrant()).isEqualTo(applicationEntity.getIsAutoGranted());
    assertThat(actual.decisionStatus())
        .isEqualTo(applicationEntity.getDecision().getOverallDecision().name());
    assertThat(actual.applicationType()).isEqualTo("INITIAL");
    assertThat(actual.version()).isEqualTo(applicationEntity.getVersion());

    // Structural assertions for nested objects
    assertThat(actual.opponents()).hasSize(1);
    assertThat(actual.provider()).isNotNull();
    assertThat(actual.provider().officeCode()).isEqualTo(applicationEntity.getOfficeCode());
    assertThat(actual.provider().contactEmail()).isEqualTo("test@example.com");
    assertThat(actual.proceedings()).hasSize(1);

    ApplicationProceedingReadModel mappedProceeding = actual.proceedings().getFirst();
    assertThat(mappedProceeding.proceedingId()).isEqualTo(proceedingEntity.getId());
    assertThat(mappedProceeding.description()).isEqualTo(proceedingEntity.getDescription());
    assertThat(mappedProceeding.proceedingType()).isEqualTo("hearing");
    assertThat(mappedProceeding.categoryOfLaw()).isEqualTo("Family");
    assertThat(mappedProceeding.matterType()).isEqualTo("SPECIAL_CHILDREN_ACT");
    assertThat(mappedProceeding.delegatedFunctionsDate()).isEqualTo(LocalDate.of(2025, 5, 6));
    assertThat(mappedProceeding.involvedChildren()).hasSize(1);
    assertThat(mappedProceeding.involvedChildren().getFirst().fullName())
        .isEqualTo(ApplicationMeritsGenerator.DEFAULT_INVOLVED_CHILD_FULL_NAME);
  }

  @Test
  void givenEmptyOpponents_whenMapped_thenOpponentsIsEmpty() {
    ApplicationMerits merits =
        DataGenerator.createDefault(
            ApplicationMeritsGenerator.class, builder -> builder.opponents(List.of()));
    ApplicationContent applicationContent =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class, builder -> builder.applicationMerits(merits));

    ApplicationEntity applicationEntity =
        newApplicationEntity(objectMapper.convertValue(applicationContent, Map.class), Set.of());

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.opponents()).isEmpty();
  }

  @Test
  void givenNoSubmitterEmail_whenMapped_thenProviderContactEmailIsNull() {
    ApplicationEntity applicationEntity =
        newApplicationEntity(Map.of("applicationMerits", Map.of()), Set.of());

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.provider()).isNotNull();
    assertThat(readModel.provider().officeCode()).isEqualTo("officeCode");
    assertThat(readModel.provider().contactEmail()).isNull();
  }

  @Test
  void givenSubmitterEmailPresent_whenMapped_thenProviderContactEmailSet() {
    ApplicationEntity applicationEntity =
        newApplicationEntity(Map.of("submitterEmail", "test@example.com"), Set.of());

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.provider()).isNotNull();
    assertThat(readModel.provider().contactEmail()).isEqualTo("test@example.com");
  }

  @Test
  void givenUnresolvableChildId_whenMapped_thenInvolvedChildrenIsEmpty() {
    UUID applyProceedingId = UUID.randomUUID();
    UUID unknownChildId = UUID.randomUUID();

    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    ApplicationContent applicationContent =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class,
            builder ->
                builder.proceedingMerits(
                    List.of(
                        ProceedingMerits.builder()
                            .proceedingId(applyProceedingId)
                            .proceedingLinkedChildren(
                                List.of(
                                    ProceedingLinkedChild.builder()
                                        .involvedChildId(unknownChildId)
                                        .build()))
                            .build())));

    ApplicationEntity applicationEntity =
        newApplicationEntity(
            objectMapper.convertValue(applicationContent, Map.class), Set.of(proceedingEntity));

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.proceedings()).hasSize(1);
    assertThat(readModel.proceedings().getFirst().involvedChildren()).isEmpty();
  }

  @Test
  void givenNoProceedingMerits_whenMapped_thenInvolvedChildrenIsEmpty() {
    UUID applyProceedingId = UUID.randomUUID();
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    ApplicationContent applicationContent =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class, builder -> builder.proceedingMerits(List.of()));

    ApplicationEntity applicationEntity =
        newApplicationEntity(
            objectMapper.convertValue(applicationContent, Map.class), Set.of(proceedingEntity));

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.proceedings()).hasSize(1);
    assertThat(readModel.proceedings().getFirst().involvedChildren()).isEmpty();
  }

  @Test
  void givenNullDecision_whenMapped_thenDecisionStatusIsNull() {
    ApplicationEntity applicationEntity = newApplicationEntity(Map.of(), Set.of());
    applicationEntity.setDecision(null);

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.decisionStatus()).isNull();
  }

  @Test
  void givenNullCaseworker_whenMapped_thenCaseworkerIdIsNull() {
    ApplicationEntity applicationEntity = newApplicationEntity(Map.of(), Set.of());
    applicationEntity.setCaseworker(null);

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.caseworkerId()).isNull();
  }

  @Test
  void givenNullProceedings_whenMapped_thenProceedingsIsEmpty() {
    ApplicationEntity applicationEntity = newApplicationEntity(Map.of(), null);

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.proceedings()).isEmpty();
  }

  @Test
  void givenNullApplicationContentAndOfficeCode_whenMapped_thenProviderIsNull() {
    ApplicationEntity applicationEntity = newApplicationEntity(null, Set.of());
    applicationEntity.setOfficeCode(null);

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.provider()).isNull();
    assertThat(readModel.opponents()).isEmpty();
  }

  @Test
  void givenMissingInvolvedChildrenCollection_whenMapped_thenInvolvedChildrenIsEmpty() {
    UUID applyProceedingId = UUID.randomUUID();
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    ApplicationMerits meritsWithoutChildren =
        DataGenerator.createDefault(
            ApplicationMeritsGenerator.class, builder -> builder.involvedChildren(null));

    ApplicationContent contentWithoutChildren =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class,
            builder ->
                builder
                    .applicationMerits(meritsWithoutChildren)
                    .proceedingMerits(
                        List.of(
                            ProceedingMerits.builder()
                                .proceedingId(applyProceedingId)
                                .proceedingLinkedChildren(
                                    List.of(
                                        ProceedingLinkedChild.builder()
                                            .involvedChildId(UUID.randomUUID())
                                            .build()))
                                .build())));

    ApplicationEntity applicationEntity =
        newApplicationEntity(
            objectMapper.convertValue(contentWithoutChildren, Map.class), Set.of(proceedingEntity));

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.proceedings().getFirst().involvedChildren()).isEmpty();
  }

  @Test
  void givenProceedingWithNullApplyProceedingId_whenMapped_thenInvolvedChildrenIsEmpty() {
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class, builder -> builder.applyProceedingId(null));

    ApplicationContent applicationContent =
        DataGenerator.createDefault(ApplicationContentGenerator.class);
    ApplicationEntity applicationEntity =
        newApplicationEntity(
            objectMapper.convertValue(applicationContent, Map.class), Set.of(proceedingEntity));

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.proceedings().getFirst().involvedChildren()).isEmpty();
  }

  @Test
  void givenProceedingWithNullScopeLimitations_whenMapped_thenScopeLimitationsIsEmpty() {
    UUID applyProceedingId = UUID.randomUUID();
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder ->
                builder
                    .applyProceedingId(applyProceedingId)
                    .proceedingContent(
                        Map.of(
                            "meaning", "hearing",
                            "matterTypeEnum", "SPECIAL_CHILDREN_ACT",
                            "categoryOfLawEnum", "Family",
                            "usedDelegatedFunctionsOn", "2025-05-06",
                            "substantiveCostLimitation", "23.45",
                            "substantiveLevelOfServiceNameEnum", "SERVICE")));

    ApplicationEntity applicationEntity = newApplicationEntity(Map.of(), Set.of(proceedingEntity));

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.proceedings()).hasSize(1);
    assertThat(readModel.proceedings().getFirst().scopeLimitations()).isEmpty();
  }

  @Test
  void
      givenApplicationContentWithoutApplicationMerits_whenMapped_thenOpponentsAndChildrenAreEmpty() {
    UUID applyProceedingId = UUID.randomUUID();
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    ApplicationEntity applicationEntity =
        newApplicationEntity(Map.of("proceedingMerits", List.of()), Set.of(proceedingEntity));

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.opponents()).isEmpty();
    assertThat(readModel.proceedings().getFirst().involvedChildren()).isEmpty();
  }

  @Test
  void givenApplicationContentWithNullProceedingMerits_whenMapped_thenInvolvedChildrenIsEmpty() {
    UUID applyProceedingId = UUID.randomUUID();
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    ApplicationContent content =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class, builder -> builder.proceedingMerits(null));
    ApplicationEntity applicationEntity =
        newApplicationEntity(
            objectMapper.convertValue(content, Map.class), Set.of(proceedingEntity));

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.proceedings().getFirst().involvedChildren()).isEmpty();
  }

  @Test
  void givenNullApplicationStatus_whenMapped_thenStatusIsNull() {
    ApplicationEntity applicationEntity = newApplicationEntity(Map.of(), Set.of());
    applicationEntity.setStatus(null);

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.status()).isNull();
  }

  @Test
  void givenDecisionWithNullOverallDecision_whenMapped_thenDecisionStatusIsNull() {
    ApplicationEntity applicationEntity = newApplicationEntity(Map.of(), Set.of());
    applicationEntity.setDecision(DecisionEntity.builder().overallDecision(null).build());

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.decisionStatus()).isNull();
  }

  @Test
  void givenProceedingMeritsDecisionWithNullDecision_whenMapped_thenMeritsDecisionIsNull() {
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(ProceedingsEntityGenerator.class);
    proceedingEntity.setMeritsDecision(
        uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity.builder()
            .decision(null)
            .build());

    ApplicationEntity applicationEntity = newApplicationEntity(Map.of(), Set.of(proceedingEntity));

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.proceedings()).hasSize(1);
    assertThat(readModel.proceedings().getFirst().meritsDecision()).isNull();
  }

  @Test
  void givenMatchedProceedingWithEmptyLinkedChildren_whenMapped_thenInvolvedChildrenIsEmpty() {
    UUID applyProceedingId = UUID.randomUUID();
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    ApplicationContent applicationContent =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class,
            builder ->
                builder.proceedingMerits(
                    List.of(
                        ProceedingMerits.builder()
                            .proceedingId(applyProceedingId)
                            .proceedingLinkedChildren(List.of())
                            .build())));

    ApplicationEntity applicationEntity =
        newApplicationEntity(
            objectMapper.convertValue(applicationContent, Map.class), Set.of(proceedingEntity));

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.proceedings().getFirst().involvedChildren()).isEmpty();
  }

  @Test
  void givenScopeLimitationsWithMissingFields_whenMapped_thenMissingFieldsMapToNull() {
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder ->
                builder.proceedingContent(
                    Map.of(
                        "meaning", "hearing",
                        "matterTypeEnum", "SPECIAL_CHILDREN_ACT",
                        "categoryOfLawEnum", "Family",
                        "usedDelegatedFunctionsOn", "2025-05-06",
                        "substantiveCostLimitation", "23.45",
                        "substantiveLevelOfServiceNameEnum", "SERVICE",
                        "scopeLimitations",
                            List.of(
                                Map.of("description", "only description"),
                                Map.of("meaning", "only meaning")))));

    ApplicationEntity applicationEntity = newApplicationEntity(Map.of(), Set.of(proceedingEntity));

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.proceedings()).hasSize(1);
    assertThat(readModel.proceedings().getFirst().scopeLimitations()).hasSize(2);
    assertThat(readModel.proceedings().getFirst().scopeLimitations().get(0).scopeLimitation())
        .isNull();
    assertThat(readModel.proceedings().getFirst().scopeLimitations().get(0).scopeDescription())
        .isEqualTo("only description");
    assertThat(readModel.proceedings().getFirst().scopeLimitations().get(1).scopeLimitation())
        .isEqualTo("only meaning");
    assertThat(readModel.proceedings().getFirst().scopeLimitations().get(1).scopeDescription())
        .isNull();
  }

  @Test
  void givenOpponentWithNullOpposable_whenMapped_thenOpponentNameFieldsAreNull() {
    ApplicationMerits merits =
        DataGenerator.createDefault(
            ApplicationMeritsGenerator.class,
            builder ->
                builder.opponents(
                    List.of(
                        OpponentDetails.builder()
                            .opposableType("ApplicationMeritsTask::Individual")
                            .opposable(null)
                            .build())));
    ApplicationContent applicationContent =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class, builder -> builder.applicationMerits(merits));

    ApplicationEntity applicationEntity =
        newApplicationEntity(objectMapper.convertValue(applicationContent, Map.class), Set.of());

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.opponents()).hasSize(1);
    assertThat(readModel.opponents().getFirst().firstName()).isNull();
    assertThat(readModel.opponents().getFirst().lastName()).isNull();
    assertThat(readModel.opponents().getFirst().organisationName()).isNull();
  }

  @Test
  void givenOfficeCodeNullAndSubmitterEmailPresent_whenMapped_thenProviderContainsEmail() {
    ApplicationEntity applicationEntity =
        newApplicationEntity(Map.of("submitterEmail", "x@y.z"), Set.of());
    applicationEntity.setOfficeCode(null);

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.provider()).isNotNull();
    assertThat(readModel.provider().officeCode()).isNull();
    assertThat(readModel.provider().contactEmail()).isEqualTo("x@y.z");
  }

  @Test
  void givenApplicationMeritsNull_whenMapped_thenOpponentsAndChildrenAreEmpty() {
    ApplicationContent applicationContent =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class, builder -> builder.applicationMerits(null));
    ApplicationEntity applicationEntity =
        newApplicationEntity(objectMapper.convertValue(applicationContent, Map.class), Set.of());

    ApplicationReadModel readModel = mapper.toApplicationReadModel(applicationEntity);

    assertThat(readModel.opponents()).isEmpty();
  }

  private ApplicationEntity newApplicationEntity(
      Map<String, Object> applicationContent, Set<ProceedingEntity> proceedings) {
    return ApplicationEntity.builder()
        .id(UUID.randomUUID())
        .version(0L)
        .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
        .laaReference("REF7327")
        .officeCode("officeCode")
        .applicationContent(applicationContent)
        .caseworker(CaseworkerEntity.builder().id(UUID.randomUUID()).username("user").build())
        .submittedAt(Instant.parse("2024-01-01T12:00:00Z"))
        .decision(DecisionEntity.builder().overallDecision(DecisionStatus.GRANTED).build())
        .usedDelegatedFunctions(false)
        .isAutoGranted(true)
        .proceedings(proceedings)
        .linkedApplications(Set.of())
        .build();
  }
}

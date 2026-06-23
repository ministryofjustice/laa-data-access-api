package uk.gov.justice.laa.dstew.access.infrastructure.jpa.getapplication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
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
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.dto.ApplicationDbProjection;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.dto.ProceedingDbProjection;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ApplicationContent;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.InvolvedChild;
import uk.gov.justice.laa.dstew.access.usecase.shared.parser.ProceedingLinkedChild;
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
  void givenFullyPopulatedEntity_whenMapped_thenAllFieldsExtracted() {
    ApplicationContent applicationContent =
        DataGenerator.createDefault(ApplicationContentGenerator.class);

    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(ProceedingsEntityGenerator.class);

    ApplicationEntity applicationEntity =
        newApplicationEntity(
            objectMapper.convertValue(applicationContent, Map.class), Set.of(proceedingEntity));

    ApplicationDbProjection actual = mapper.toApplicationDbProjection(applicationEntity);

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
    assertThat(actual.version()).isEqualTo(applicationEntity.getVersion());
    assertThat(actual.officeCode()).isEqualTo(applicationEntity.getOfficeCode());
    assertThat(actual.submitterEmail()).isEqualTo(applicationContent.getSubmitterEmail());
    assertThat(actual.opponents())
        .hasSize(applicationContent.getApplicationMerits().getOpponents().size());
    assertThat(actual.proceedings()).hasSize(1);
  }

  @Test
  void givenFullyPopulatedProceedingEntity_whenMapped_thenAllFieldsExtracted() {
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder ->
                builder.meritsDecision(
                    uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity.builder()
                        .decision(
                            uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus.GRANTED)
                        .build()));

    ProceedingDbProjection actual =
        mapper.toProceedingDbProjection(
            proceedingEntity, Collections.emptyList(), Collections.emptyList());

    assertThat(actual.proceedingId()).isEqualTo(proceedingEntity.getId());
    assertThat(actual.description()).isEqualTo(proceedingEntity.getDescription());
    assertThat(actual.meritsDecision())
        .isEqualTo(proceedingEntity.getMeritsDecision().getDecision().name());
    assertThat(actual.proceedingType()).isEqualTo("hearing");
    assertThat(actual.categoryOfLaw()).isEqualTo("Family");
    assertThat(actual.matterType()).isEqualTo("SPECIAL_CHILDREN_ACT");
    assertThat(actual.delegatedFunctionsDate()).isEqualTo(LocalDate.of(2025, 5, 6));
    assertThat(actual.involvedChildren()).isEmpty();
  }

  @Test
  void givenNullDecision_whenMapped_thenDecisionStatusIsNull() {
    ApplicationEntity applicationEntity = newApplicationEntity(Map.of(), Set.of());
    applicationEntity.setDecision(null);

    ApplicationDbProjection actual = mapper.toApplicationDbProjection(applicationEntity);

    assertThat(actual.decisionStatus()).isNull();
  }

  @Test
  void givenDecisionWithNullOverallDecision_whenMapped_thenDecisionStatusIsNull() {
    ApplicationEntity applicationEntity = newApplicationEntity(Map.of(), Set.of());
    applicationEntity.setDecision(DecisionEntity.builder().overallDecision(null).build());

    ApplicationDbProjection actual = mapper.toApplicationDbProjection(applicationEntity);

    assertThat(actual.decisionStatus()).isNull();
  }

  @Test
  void givenNullCaseworker_whenMapped_thenCaseworkerIdIsNull() {
    ApplicationEntity applicationEntity = newApplicationEntity(Map.of(), Set.of());
    applicationEntity.setCaseworker(null);

    ApplicationDbProjection actual = mapper.toApplicationDbProjection(applicationEntity);

    assertThat(actual.caseworkerId()).isNull();
  }

  @Test
  void givenNullApplicationStatus_whenMapped_thenStatusIsNull() {
    ApplicationEntity applicationEntity = newApplicationEntity(Map.of(), Set.of());
    applicationEntity.setStatus(null);

    ApplicationDbProjection actual = mapper.toApplicationDbProjection(applicationEntity);

    assertThat(actual.status()).isNull();
  }

  @Test
  void givenNullProceedings_whenMapped_thenProceedingsIsEmpty() {
    ApplicationEntity applicationEntity = newApplicationEntity(Map.of(), null);

    ApplicationDbProjection actual = mapper.toApplicationDbProjection(applicationEntity);

    assertThat(actual.proceedings()).isEmpty();
  }

  @Test
  void givenNullApplicationContent_whenMapped_thenParsedFieldsAreEmpty() {
    ApplicationEntity applicationEntity = newApplicationEntity(null, Set.of());

    ApplicationDbProjection actual = mapper.toApplicationDbProjection(applicationEntity);

    assertThat(actual.submitterEmail()).isNull();
    assertThat(actual.opponents()).isEmpty();
  }

  @Test
  void givenNullMeritsDecisionOnProceeding_whenMapped_thenMeritsDecisionIsNull() {
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(ProceedingsEntityGenerator.class);
    proceedingEntity.setMeritsDecision(null);

    ProceedingDbProjection actual =
        mapper.toProceedingDbProjection(
            proceedingEntity, Collections.emptyList(), Collections.emptyList());

    assertThat(actual.meritsDecision()).isNull();
  }

  @Test
  void givenMeritsDecisionWithNullDecision_whenMapped_thenMeritsDecisionIsNull() {
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(ProceedingsEntityGenerator.class);
    proceedingEntity.setMeritsDecision(MeritsDecisionEntity.builder().decision(null).build());

    ProceedingDbProjection actual =
        mapper.toProceedingDbProjection(
            proceedingEntity, Collections.emptyList(), Collections.emptyList());

    assertThat(actual.meritsDecision()).isNull();
  }

  @Test
  void givenApplicationMeritsNull_whenMapped_thenOpponentsAndChildrenAreEmpty() {
    ApplicationContent applicationContent =
        DataGenerator.createDefault(
            ApplicationContentGenerator.class, builder -> builder.applicationMerits(null));
    ApplicationEntity applicationEntity =
        newApplicationEntity(objectMapper.convertValue(applicationContent, Map.class), Set.of());

    ApplicationDbProjection actual = mapper.toApplicationDbProjection(applicationEntity);

    assertThat(actual.opponents()).isEmpty();
    assertThat(actual.proceedings()).isEmpty();
  }

  @Test
  void givenMatchingMeritsWithChildren_whenMapped_thenInvolvedChildrenResolvedOnProceeding() {
    UUID applyProceedingId = UUID.randomUUID();
    UUID childId = UUID.randomUUID();

    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    InvolvedChild child =
        InvolvedChild.builder()
            .id(childId)
            .fullName("John Smith")
            .dateOfBirth(LocalDate.of(2020, 1, 1))
            .build();

    ProceedingMerits merits =
        ProceedingMerits.builder()
            .proceedingId(applyProceedingId)
            .proceedingLinkedChildren(
                List.of(ProceedingLinkedChild.builder().involvedChildId(childId).build()))
            .build();

    ProceedingDbProjection actual =
        mapper.toProceedingDbProjection(proceedingEntity, List.of(merits), List.of(child));

    assertThat(actual.involvedChildren()).hasSize(1);
    assertThat(actual.involvedChildren().getFirst().getFullName()).isEqualTo("John Smith");
    assertThat(actual.involvedChildren().getFirst().getDateOfBirth())
        .isEqualTo(LocalDate.of(2020, 1, 1));
  }

  @Test
  void givenNoMatchingMeritsForProceeding_whenMapped_thenInvolvedChildrenIsEmpty() {
    UUID applyProceedingId = UUID.randomUUID();

    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    ProceedingMerits merits =
        ProceedingMerits.builder()
            .proceedingId(UUID.randomUUID())
            .proceedingLinkedChildren(
                List.of(ProceedingLinkedChild.builder().involvedChildId(UUID.randomUUID()).build()))
            .build();

    ProceedingDbProjection actual =
        mapper.toProceedingDbProjection(
            proceedingEntity,
            List.of(merits),
            List.of(InvolvedChild.builder().id(UUID.randomUUID()).build()));

    assertThat(actual.involvedChildren()).isEmpty();
  }

  @Test
  void givenNullApplyProceedingId_whenMapped_thenInvolvedChildrenIsEmpty() {
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class, builder -> builder.applyProceedingId(null));

    ProceedingDbProjection actual =
        mapper.toProceedingDbProjection(
            proceedingEntity,
            List.of(ProceedingMerits.builder().proceedingId(UUID.randomUUID()).build()),
            List.of(InvolvedChild.builder().id(UUID.randomUUID()).build()));

    assertThat(actual.involvedChildren()).isEmpty();
  }

  @Test
  void givenMatchingMeritsWithNullLinkedChildren_whenMapped_thenInvolvedChildrenIsEmpty() {
    UUID applyProceedingId = UUID.randomUUID();
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    ProceedingMerits merits =
        ProceedingMerits.builder()
            .proceedingId(applyProceedingId)
            .proceedingLinkedChildren(null)
            .build();

    ProceedingDbProjection actual =
        mapper.toProceedingDbProjection(
            proceedingEntity,
            List.of(merits),
            List.of(InvolvedChild.builder().id(UUID.randomUUID()).build()));

    assertThat(actual.involvedChildren()).isEmpty();
  }

  @Test
  void givenMatchingMeritsWithEmptyLinkedChildren_whenMapped_thenInvolvedChildrenIsEmpty() {
    UUID applyProceedingId = UUID.randomUUID();
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    ProceedingMerits merits =
        ProceedingMerits.builder()
            .proceedingId(applyProceedingId)
            .proceedingLinkedChildren(Collections.emptyList())
            .build();

    ProceedingDbProjection actual =
        mapper.toProceedingDbProjection(
            proceedingEntity,
            List.of(merits),
            List.of(InvolvedChild.builder().id(UUID.randomUUID()).build()));

    assertThat(actual.involvedChildren()).isEmpty();
  }

  @Test
  void
      givenMatchingMeritsWithLinkedChildrenButNullInvolvedChildren_whenMapped_thenInvolvedChildrenIsEmpty() {
    UUID applyProceedingId = UUID.randomUUID();
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    ProceedingMerits merits =
        ProceedingMerits.builder()
            .proceedingId(applyProceedingId)
            .proceedingLinkedChildren(
                List.of(ProceedingLinkedChild.builder().involvedChildId(UUID.randomUUID()).build()))
            .build();

    ProceedingDbProjection actual =
        mapper.toProceedingDbProjection(proceedingEntity, List.of(merits), null);

    assertThat(actual.involvedChildren()).isEmpty();
  }

  @Test
  void
      givenMatchingMeritsWithLinkedChildrenButEmptyInvolvedChildren_whenMapped_thenInvolvedChildrenIsEmpty() {
    UUID applyProceedingId = UUID.randomUUID();
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    ProceedingMerits merits =
        ProceedingMerits.builder()
            .proceedingId(applyProceedingId)
            .proceedingLinkedChildren(
                List.of(ProceedingLinkedChild.builder().involvedChildId(UUID.randomUUID()).build()))
            .build();

    ProceedingDbProjection actual =
        mapper.toProceedingDbProjection(proceedingEntity, List.of(merits), Collections.emptyList());

    assertThat(actual.involvedChildren()).isEmpty();
  }

  @Test
  void givenUnresolvableChildId_whenMapped_thenInvolvedChildrenIsEmpty() {
    UUID applyProceedingId = UUID.randomUUID();
    UUID childId = UUID.randomUUID();

    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    ProceedingMerits merits =
        ProceedingMerits.builder()
            .proceedingId(applyProceedingId)
            .proceedingLinkedChildren(
                List.of(ProceedingLinkedChild.builder().involvedChildId(childId).build()))
            .build();

    ProceedingDbProjection actual =
        mapper.toProceedingDbProjection(
            proceedingEntity,
            List.of(merits),
            List.of(InvolvedChild.builder().id(UUID.randomUUID()).build()));

    assertThat(actual.involvedChildren()).isEmpty();
  }

  @Test
  void givenSubmitterEmailPresent_whenMapped_thenSubmitterEmailExtracted() {
    ApplicationEntity applicationEntity =
        newApplicationEntity(
            Map.of("submitterEmail", ApplicationMeritsGenerator.DEFAULT_INVOLVED_CHILD_FULL_NAME),
            Set.of());

    ApplicationDbProjection actual = mapper.toApplicationDbProjection(applicationEntity);

    assertThat(actual.submitterEmail())
        .isEqualTo(ApplicationMeritsGenerator.DEFAULT_INVOLVED_CHILD_FULL_NAME);
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

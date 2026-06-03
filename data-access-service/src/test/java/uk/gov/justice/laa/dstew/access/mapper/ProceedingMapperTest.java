package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceedingResponse;
import uk.gov.justice.laa.dstew.access.model.InvolvedChild;
import uk.gov.justice.laa.dstew.access.model.InvolvedChildResponse;
import uk.gov.justice.laa.dstew.access.model.Proceeding;
import uk.gov.justice.laa.dstew.access.model.ProceedingLinkedChild;
import uk.gov.justice.laa.dstew.access.model.ProceedingMerits;
import uk.gov.justice.laa.dstew.access.model.ScopeLimitationResponse;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationMeritsGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.InvolvedChildGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingMeritsGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;

@ExtendWith(MockitoExtension.class)
class ProceedingMapperTest extends BaseMapperTest {

  @InjectMocks private ProceedingMapperImpl proceedingMapper;

  @Test
  void givenNullProceeding_whenToProceedingEntity_thenReturnNull() {
    assertThat(proceedingMapper.toProceedingEntity(null, UUID.randomUUID())).isNull();
  }

  @Test
  void givenProceedingAndApplicationId_whenToProceedingEntity_thenMapsFieldsCorrectly() {
    UUID applicationId = UUID.randomUUID();
    Proceeding proceeding = DataGenerator.createDefault(ProceedingGenerator.class);

    ProceedingEntity result = proceedingMapper.toProceedingEntity(proceeding, applicationId);

    assertThat(result.getApplyProceedingId()).isEqualTo(proceeding.getId());
    assertThat(result.getProceedingContent())
        .isEqualTo(objectMapper.convertValue(proceeding, Map.class));
    assertThat(result.isLead()).isEqualTo(proceeding.getLeadProceeding());
    assertThat(result.getDescription()).isEqualTo(proceeding.getDescription());
  }

  @Test
  void givenProceedingWithAllNullFields_whenToProceedingEntity_thenNullableFieldsAreNull() {
    Proceeding proceeding =
        DataGenerator.createDefault(
            ProceedingGenerator.class,
            builder -> builder.id(null).leadProceeding(null).description(null));

    ProceedingEntity result = proceedingMapper.toProceedingEntity(proceeding, null);

    assertThat(result.getApplyProceedingId()).isNull();
    assertThat(result.isLead()).isFalse();
    assertThat(result.getDescription()).isNull();
  }

  @Test
  void givenNullProceedingEntity_whenToApplicationProceeding_thenReturnNull() {
    assertThat(proceedingMapper.toApplicationProceeding(null)).isNull();
  }

  @Test
  void givenProceedingEntity_whenToApplicationProceeding_thenMapsFieldsCorrectly() {
    UUID proceedingId = UUID.randomUUID();
    ProceedingEntity entity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class, builder -> builder.id(proceedingId));

    ApplicationProceedingResponse result = proceedingMapper.toApplicationProceeding(entity);

    assertThat(result).isNotNull();
    assertThat(result.getProceedingId()).isEqualTo(proceedingId);
    assertThat(result.getProceedingDescription()).isEqualTo("description");
    assertThat(result.getProceedingType()).isEqualTo("hearing");
    assertThat(result.getCategoryOfLaw().getValue()).isEqualToIgnoringCase("Family");
    assertThat(result.getMatterType().getValue()).isEqualToIgnoringCase("SPECIAL_CHILDREN_ACT");
    assertThat(result.getLevelOfService()).isEqualTo("service");
    assertThat(result.getSubstantiveCostLimitation()).isEqualTo(23.45);
    assertThat(result.getDelegatedFunctionsDate()).isEqualTo(LocalDate.parse("2025-05-06"));
    assertThat(result.getScopeLimitations()).isNotNull().hasSize(1);
  }

  @Test
  void
      givenProceedingEntityWithAllNullContentFields_whenToApplicationProceeding_thenAllContentFieldsAreNull() {
    ProceedingEntity entity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.id(null).description(null).proceedingContent(Map.of()));

    ApplicationProceedingResponse result = proceedingMapper.toApplicationProceeding(entity);

    assertThat(result.getProceedingId()).isNull();
    assertThat(result.getProceedingDescription()).isNull();
    assertThat(result.getProceedingType()).isNull();
    assertThat(result.getCategoryOfLaw()).isNull();
    assertThat(result.getMatterType()).isNull();
    assertThat(result.getLevelOfService()).isNull();
    assertThat(result.getSubstantiveCostLimitation()).isNull();
    assertThat(result.getDelegatedFunctionsDate()).isNull();
    assertThat(result.getScopeLimitations()).isEmpty();
  }

  @Test
  void
      givenProceedingEntityWithScopeLimitations_whenToApplicationProceeding_thenMapsScopeLimitationsCorrectly() {
    ProceedingEntity entity = DataGenerator.createDefault(ProceedingsEntityGenerator.class);

    ApplicationProceedingResponse result = proceedingMapper.toApplicationProceeding(entity);

    assertThat(result.getScopeLimitations()).isNotNull().hasSize(1);
    ScopeLimitationResponse scopeLimitation = result.getScopeLimitations().get(0);
    assertThat(scopeLimitation.getScopeLimitation()).isEqualTo("hearing");
    assertThat(scopeLimitation.getScopeDescription())
        .isEqualTo("Hearing scope limitation description");
  }

  @Test
  void
      givenProceedingEntityWithScopeLimitationsMissingDescription_whenToApplicationProceeding_thenDescriptionIsNull() {
    ProceedingEntity entity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder ->
                builder.proceedingContent(
                    Map.of(
                        "meaning",
                        "hearing",
                        "scopeLimitations",
                        List.of(
                            Map.of("meaning", "hearing only")
                            // description field intentionally missing
                            ))));

    ApplicationProceedingResponse result = proceedingMapper.toApplicationProceeding(entity);

    assertThat(result.getScopeLimitations()).isNotNull().hasSize(1);
    ScopeLimitationResponse scopeLimitation = result.getScopeLimitations().get(0);
    assertThat(scopeLimitation.getScopeLimitation()).isEqualTo("hearing only");
    assertThat(scopeLimitation.getScopeDescription()).isNull();
  }

  @Test
  void
      givenProceedingEntityWithScopeLimitationsMissingMeaning_whenToApplicationProceeding_thenScopeLimitationIsNull() {
    ProceedingEntity entity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder ->
                builder.proceedingContent(
                    Map.of(
                        "meaning",
                        "hearing",
                        "scopeLimitations",
                        List.of(
                            Map.of("description", "Some description")
                            // meaning field intentionally missing
                            ))));

    ApplicationProceedingResponse result = proceedingMapper.toApplicationProceeding(entity);

    assertThat(result.getScopeLimitations()).isNotNull().hasSize(1);
    ScopeLimitationResponse scopeLimitation = result.getScopeLimitations().get(0);
    assertThat(scopeLimitation.getScopeLimitation()).isNull();
    assertThat(scopeLimitation.getScopeDescription()).isEqualTo("Some description");
  }

  @Test
  void givenNullProceedingEntity_whenToApplicationProceedingWithChildren_thenReturnNull() {
    assertThat(
            proceedingMapper.toApplicationProceeding(
                null, Collections.emptyList(), Collections.emptyList()))
        .isNull();
  }

  @Test
  void
      givenMatchingProceedingMerits_whenToApplicationProceedingWithChildren_thenMapsInvolvedChildrenCorrectly() {
    UUID applyProceedingId = UUID.randomUUID();
    UUID childId = ApplicationMeritsGenerator.DEFAULT_INVOLVED_CHILD_ID;

    ProceedingEntity entity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    InvolvedChild child = DataGenerator.createDefault(InvolvedChildGenerator.class);

    ProceedingMerits merits =
        DataGenerator.createDefault(
            ProceedingMeritsGenerator.class,
            builder ->
                builder
                    .proceedingId(applyProceedingId)
                    .proceedingLinkedChildren(
                        List.of(ProceedingLinkedChild.builder().involvedChildId(childId).build())));

    ApplicationProceedingResponse result =
        proceedingMapper.toApplicationProceeding(entity, List.of(merits), List.of(child));

    assertThat(result).isNotNull();
    assertThat(result.getInvolvedChildren()).isNotNull().hasSize(1);
    InvolvedChildResponse involvedChild = result.getInvolvedChildren().get(0);
    assertThat(involvedChild.getFullName())
        .isEqualTo(ApplicationMeritsGenerator.DEFAULT_INVOLVED_CHILD_FULL_NAME);
    assertThat(involvedChild.getDateOfBirth())
        .isEqualTo(ApplicationMeritsGenerator.DEFAULT_INVOLVED_CHILD_DATE_OF_BIRTH);
  }

  @Test
  void
      givenNoMatchingProceedingMerits_whenToApplicationProceedingWithChildren_thenInvolvedChildrenIsEmpty() {
    UUID applyProceedingId = UUID.randomUUID();

    ProceedingEntity entity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    // merits reference a different proceedingId — no match expected
    ProceedingMerits meritsForOtherProceeding =
        DataGenerator.createDefault(
            ProceedingMeritsGenerator.class, builder -> builder.proceedingId(UUID.randomUUID()));

    InvolvedChild child = DataGenerator.createDefault(InvolvedChildGenerator.class);

    ApplicationProceedingResponse result =
        proceedingMapper.toApplicationProceeding(
            entity, List.of(meritsForOtherProceeding), List.of(child));

    assertThat(result).isNotNull();
    assertThat(result.getInvolvedChildren()).isEmpty();
  }

  @Test
  void
      givenNullProceedingMeritsList_whenToApplicationProceedingWithChildren_thenInvolvedChildrenNotSet() {
    ProceedingEntity entity = DataGenerator.createDefault(ProceedingsEntityGenerator.class);

    ApplicationProceedingResponse result =
        proceedingMapper.toApplicationProceeding(entity, null, Collections.emptyList());

    assertThat(result).isNotNull();
    assertThat(result.getInvolvedChildren()).isEmpty();
  }

  @Test
  void
      givenLinkedChildIdNotFoundInInvolvedChildren_whenToApplicationProceedingWithChildren_thenChildSkipped() {
    UUID applyProceedingId = UUID.randomUUID();
    UUID unknownChildId = UUID.randomUUID();

    ProceedingEntity entity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    ProceedingMerits merits =
        DataGenerator.createDefault(
            ProceedingMeritsGenerator.class,
            builder ->
                builder
                    .proceedingId(applyProceedingId)
                    .proceedingLinkedChildren(
                        List.of(
                            ProceedingLinkedChild.builder()
                                .involvedChildId(unknownChildId)
                                .build())));

    // involvedChildren list does not contain unknownChildId
    InvolvedChild otherChild = DataGenerator.createDefault(InvolvedChildGenerator.class);

    ApplicationProceedingResponse result =
        proceedingMapper.toApplicationProceeding(entity, List.of(merits), List.of(otherChild));

    assertThat(result).isNotNull();
    assertThat(result.getInvolvedChildren()).isEmpty();
  }

  @Test
  void
      givenMultipleMatchingChildren_whenToApplicationProceedingWithChildren_thenAllChildrenMapped() {
    UUID applyProceedingId = UUID.randomUUID();
    UUID childId1 = UUID.randomUUID();
    UUID childId2 = UUID.randomUUID();
    LocalDate dob1 = LocalDate.of(2018, 6, 1);
    LocalDate dob2 = LocalDate.of(2020, 11, 20);

    ProceedingEntity entity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class,
            builder -> builder.applyProceedingId(applyProceedingId));

    InvolvedChild child1 =
        InvolvedChild.builder().id(childId1).fullName("Alice").dateOfBirth(dob1).build();
    InvolvedChild child2 =
        InvolvedChild.builder().id(childId2).fullName("Bob").dateOfBirth(dob2).build();

    ProceedingMerits merits =
        ProceedingMerits.builder()
            .proceedingId(applyProceedingId)
            .proceedingLinkedChildren(
                List.of(
                    ProceedingLinkedChild.builder().involvedChildId(childId1).build(),
                    ProceedingLinkedChild.builder().involvedChildId(childId2).build()))
            .build();

    ApplicationProceedingResponse result =
        proceedingMapper.toApplicationProceeding(entity, List.of(merits), List.of(child1, child2));

    assertThat(result).isNotNull();
    assertThat(result.getInvolvedChildren()).hasSize(2);
    assertThat(result.getInvolvedChildren())
        .extracting(InvolvedChildResponse::getFullName)
        .containsExactly("Alice", "Bob");
    assertThat(result.getInvolvedChildren())
        .extracting(InvolvedChildResponse::getDateOfBirth)
        .containsExactly(dob1, dob2);
  }
}

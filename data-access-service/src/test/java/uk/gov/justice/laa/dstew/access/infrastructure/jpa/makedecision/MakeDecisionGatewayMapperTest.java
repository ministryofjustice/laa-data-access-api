package uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.DecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.domain.enums.DecisionStatus;
import uk.gov.justice.laa.dstew.access.domain.enums.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.ApplicationDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.MeritsDecisionDomainGenerator;

class MakeDecisionGatewayMapperTest {

  private MakeDecisionGatewayMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new MakeDecisionGatewayMapper();
  }

  // ── applyDecisionToEntity ────────────────────────────────────────────────

  @Test
  void applyDecisionToEntity_updatesDecisionIsAutoGrantedAndMeritsDecision() {
    UUID proceedingId = UUID.randomUUID();
    ProceedingEntity proceedingEntity = ProceedingEntity.builder().id(proceedingId).build();
    ApplicationEntity entity =
        ApplicationEntity.builder()
            .version(5L)
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .isAutoGranted(false)
            .proceedings(Set.of(proceedingEntity))
            .build();

    MeritsDecisionDomain meritsDomain =
        DataGenerator.createDefault(
            MeritsDecisionDomainGenerator.class,
            b -> b.decision(MeritsDecisionStatus.GRANTED).reason("r").justification("j"));
    ProceedingDomain proceedingDomain =
        ProceedingDomain.builder().id(proceedingId).meritsDecision(meritsDomain).build();
    DecisionDomain decisionDomain =
        DecisionDomain.builder()
            .overallDecision(DecisionStatus.GRANTED)
            .modifiedAt(Instant.now())
            .build();
    ApplicationDomain domain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class,
            b ->
                b.decision(decisionDomain)
                    .isAutoGranted(true)
                    .proceedings(Set.of(proceedingDomain)));

    mapper.applyDecisionToEntity(entity, domain);

    assertThat(entity.getVersion()).isEqualTo(5L);
    assertThat(entity.getDecision().getOverallDecision())
        .isEqualTo(uk.gov.justice.laa.dstew.access.model.DecisionStatus.GRANTED);
    assertThat(entity.getIsAutoGranted()).isTrue();
    var meritsDecision = entity.getProceedings().iterator().next().getMeritsDecision();
    assertThat(meritsDecision).isNotNull();
    assertThat(meritsDecision.getDecision())
        .isEqualTo(uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus.GRANTED);
    assertThat(meritsDecision.getReason()).isEqualTo("r");
    assertThat(meritsDecision.getJustification()).isEqualTo("j");
  }

  @Test
  void applyDecisionToEntity_withExistingDecisionEntity_reusesExistingDecisionEntity() {
    DecisionEntity existingDecision =
        DecisionEntity.builder()
            .overallDecision(uk.gov.justice.laa.dstew.access.model.DecisionStatus.REFUSED)
            .build();
    ApplicationEntity entity =
        ApplicationEntity.builder()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .decision(existingDecision)
            .build();
    ApplicationDomain domain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class,
            b ->
                b.decision(
                        DecisionDomain.builder()
                            .overallDecision(DecisionStatus.GRANTED)
                            .modifiedAt(Instant.now())
                            .build())
                    .proceedings(Set.of()));

    mapper.applyDecisionToEntity(entity, domain);

    assertThat(entity.getDecision()).isSameAs(existingDecision);
    assertThat(entity.getDecision().getOverallDecision())
        .isEqualTo(uk.gov.justice.laa.dstew.access.model.DecisionStatus.GRANTED);
  }

  @Test
  void applyDecisionToEntity_withNullDomainProceedings_skipsProceedingsBlock() {
    UUID proceedingId = UUID.randomUUID();
    ProceedingEntity proceedingEntity = ProceedingEntity.builder().id(proceedingId).build();
    ApplicationEntity entity =
        ApplicationEntity.builder()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .proceedings(Set.of(proceedingEntity))
            .build();
    ApplicationDomain domain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class,
            b ->
                b.decision(
                        DecisionDomain.builder()
                            .overallDecision(DecisionStatus.REFUSED)
                            .modifiedAt(Instant.now())
                            .build())
                    .proceedings(null));

    mapper.applyDecisionToEntity(entity, domain);

    assertThat(proceedingEntity.getMeritsDecision()).isNull();
  }

  @Test
  void applyDecisionToEntity_withDomainProceedingNotMatchingAnyEntity_skipsThatProceeding() {
    UUID entityProceedingId = UUID.randomUUID();
    UUID unmatchedDomainProceedingId = UUID.randomUUID();
    ProceedingEntity proceedingEntity = ProceedingEntity.builder().id(entityProceedingId).build();
    ApplicationEntity entity =
        ApplicationEntity.builder()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .proceedings(Set.of(proceedingEntity))
            .build();

    MeritsDecisionDomain meritsDomain =
        DataGenerator.createDefault(MeritsDecisionDomainGenerator.class);
    ProceedingDomain unmatchedDomain =
        ProceedingDomain.builder()
            .id(unmatchedDomainProceedingId)
            .meritsDecision(meritsDomain)
            .build();
    ApplicationDomain domain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class,
            b ->
                b.decision(
                        DecisionDomain.builder()
                            .overallDecision(DecisionStatus.REFUSED)
                            .modifiedAt(Instant.now())
                            .build())
                    .proceedings(Set.of(unmatchedDomain)));

    mapper.applyDecisionToEntity(entity, domain);

    assertThat(proceedingEntity.getMeritsDecision()).isNull();
  }

  @Test
  void applyDecisionToEntity_withProceedingHavingNullMeritsDecision_doesNotApplyMeritsDecision() {
    UUID proceedingId = UUID.randomUUID();
    ProceedingEntity proceedingEntity = ProceedingEntity.builder().id(proceedingId).build();
    ApplicationEntity entity =
        ApplicationEntity.builder()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .proceedings(Set.of(proceedingEntity))
            .build();

    ProceedingDomain proceedingDomain =
        ProceedingDomain.builder().id(proceedingId).meritsDecision(null).build();
    ApplicationDomain domain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class,
            b ->
                b.decision(
                        DecisionDomain.builder()
                            .overallDecision(DecisionStatus.REFUSED)
                            .modifiedAt(Instant.now())
                            .build())
                    .proceedings(Set.of(proceedingDomain)));

    mapper.applyDecisionToEntity(entity, domain);

    assertThat(proceedingEntity.getMeritsDecision()).isNull();
  }

  @Test
  void applyDecisionToEntity_withNullOverallDecisionInDomain_setsNullOnEntity() {
    ApplicationEntity entity =
        ApplicationEntity.builder().status(ApplicationStatus.APPLICATION_IN_PROGRESS).build();
    ApplicationDomain domain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class,
            b ->
                b.decision(
                        DecisionDomain.builder()
                            .overallDecision(null)
                            .modifiedAt(Instant.now())
                            .build())
                    .proceedings(Set.of()));

    mapper.applyDecisionToEntity(entity, domain);

    assertThat(entity.getDecision().getOverallDecision()).isNull();
  }

  @Test
  void applyDecisionToEntity_withNullDecisionInsideMeritsDecision_setsNullDecisionOnEntity() {
    UUID proceedingId = UUID.randomUUID();
    ProceedingEntity proceedingEntity = ProceedingEntity.builder().id(proceedingId).build();
    ApplicationEntity entity =
        ApplicationEntity.builder()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .proceedings(Set.of(proceedingEntity))
            .build();

    MeritsDecisionDomain meritsDomain =
        MeritsDecisionDomain.builder().decision(null).reason("r").justification("j").build();
    ProceedingDomain proceedingDomain =
        ProceedingDomain.builder().id(proceedingId).meritsDecision(meritsDomain).build();
    ApplicationDomain domain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class,
            b ->
                b.decision(
                        DecisionDomain.builder()
                            .overallDecision(DecisionStatus.REFUSED)
                            .modifiedAt(Instant.now())
                            .build())
                    .proceedings(Set.of(proceedingDomain)));

    mapper.applyDecisionToEntity(entity, domain);

    assertThat(proceedingEntity.getMeritsDecision().getDecision()).isNull();
  }
}

package uk.gov.justice.laa.dstew.access.infrastructure.jpa.makedecision;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.CertificateDomain;
import uk.gov.justice.laa.dstew.access.domain.DecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.domain.enums.DecisionStatus;
import uk.gov.justice.laa.dstew.access.domain.enums.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.CertificateEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.ApplicationDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.CertificateDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.MeritsDecisionDomainGenerator;

class MakeDecisionGatewayMapperTest {

  private MakeDecisionGatewayMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new MakeDecisionGatewayMapper();
  }

  // ── toCertificateDomain ──────────────────────────────────────────────────

  @Test
  void givenFullyPopulatedEntity_whenToCertificateDomain_thenAllFieldsMapped() {
    UUID id = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    CertificateEntity entity = new CertificateEntity();
    entity.setId(id);
    entity.setApplicationId(applicationId);
    entity.setCertificateContent(Map.of("key", "value"));
    entity.setCreatedBy("creator");
    entity.setUpdatedBy("updater");

    CertificateDomain domain = mapper.toCertificateDomain(entity);

    assertThat(domain.id()).isEqualTo(id);
    assertThat(domain.applicationId()).isEqualTo(applicationId);
    assertThat(domain.certificateContent()).isEqualTo(Map.of("key", "value"));
    assertThat(domain.createdBy()).isEqualTo("creator");
    assertThat(domain.updatedBy()).isEqualTo("updater");
  }

  @Test
  void givenCertificateDomain_whenToCertificateEntity_thenNonHibernateFieldsSetAndIdIsNull() {
    CertificateDomain domain =
        DataGenerator.createDefault(CertificateDomainGenerator.class, b -> b.id(null));

    CertificateEntity entity = mapper.toCertificateEntity(domain);

    assertThat(entity.getId()).isNull();
    assertThat(entity.getApplicationId()).isEqualTo(domain.applicationId());
    assertThat(entity.getCertificateContent()).isEqualTo(domain.certificateContent());
    assertThat(entity.getCreatedBy()).isEqualTo(domain.createdBy());
    assertThat(entity.getUpdatedBy()).isEqualTo(domain.updatedBy());
  }

  @Test
  void givenCertificateDomainWithNullCreatedBy_whenToCertificateEntity_thenCreatedByIsNull() {
    CertificateDomain domain =
        DataGenerator.createDefault(
            CertificateDomainGenerator.class, b -> b.id(null).createdBy(null).updatedBy(null));

    CertificateEntity entity = mapper.toCertificateEntity(domain);

    assertThat(entity.getCreatedBy()).isNull();
    assertThat(entity.getUpdatedBy()).isNull();
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
    assertThat(entity.getProceedings().iterator().next().getMeritsDecision()).isNotNull();
    assertThat(entity.getProceedings().iterator().next().getMeritsDecision().getDecision())
        .isEqualTo(uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus.GRANTED);
    assertThat(entity.getProceedings().iterator().next().getMeritsDecision().getReason())
        .isEqualTo("r");
    assertThat(entity.getProceedings().iterator().next().getMeritsDecision().getJustification())
        .isEqualTo("j");
  }

  @Test
  void applyDecisionToEntity_withNullExistingDecision_createsNewDecisionEntity() {
    ApplicationEntity entity =
        ApplicationEntity.builder()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .decision(null)
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
                    .proceedings(Set.of()));

    mapper.applyDecisionToEntity(entity, domain);

    assertThat(entity.getDecision()).isNotNull();
    assertThat(entity.getDecision().getOverallDecision())
        .isEqualTo(uk.gov.justice.laa.dstew.access.model.DecisionStatus.REFUSED);
  }

  @Test
  void applyDecisionToEntity_withNullExistingMeritsDecision_createsNewMeritsDecisionEntity() {
    UUID proceedingId = UUID.randomUUID();
    ProceedingEntity proceedingEntity =
        ProceedingEntity.builder().id(proceedingId).meritsDecision(null).build();
    ApplicationEntity entity =
        ApplicationEntity.builder()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .proceedings(Set.of(proceedingEntity))
            .build();

    MeritsDecisionDomain meritsDomain =
        DataGenerator.createDefault(
            MeritsDecisionDomainGenerator.class,
            b -> b.decision(MeritsDecisionStatus.REFUSED).reason("r").justification("j"));
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

    assertThat(proceedingEntity.getMeritsDecision()).isNotNull();
    assertThat(proceedingEntity.getMeritsDecision().getDecision())
        .isEqualTo(uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus.REFUSED);
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

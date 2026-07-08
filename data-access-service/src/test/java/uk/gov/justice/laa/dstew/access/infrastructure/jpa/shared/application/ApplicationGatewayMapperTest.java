package uk.gov.justice.laa.dstew.access.infrastructure.jpa.shared.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.DecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.IndividualDomain;
import uk.gov.justice.laa.dstew.access.domain.MeritsDecisionDomain;
import uk.gov.justice.laa.dstew.access.domain.ProceedingDomain;
import uk.gov.justice.laa.dstew.access.domain.enums.DecisionStatus;
import uk.gov.justice.laa.dstew.access.domain.enums.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.IndividualEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.ApplicationDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.IndividualDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.domain.ProceedingDomainGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.individual.IndividualEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;

class ApplicationGatewayMapperTest {

  private ApplicationGatewayMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ApplicationGatewayMapper();
  }

  @Test
  void toApplicationDomain_mapsAllFieldsFromEntity() {
    UUID entityId = UUID.randomUUID();
    ApplicationEntity entity =
        DataGenerator.createDefault(ApplicationEntityGenerator.class, b -> b.id(entityId));
    ApplicationDomain domain = mapper.toApplicationDomain(entity);

    assertThat(domain.id()).isEqualTo(entityId);
    assertThat(domain.version()).isEqualTo(entity.getVersion());
    assertThat(domain.status()).isEqualTo(entity.getStatus().name());
    assertThat(domain.laaReference()).isEqualTo(entity.getLaaReference());
    assertThat(domain.officeCode()).isEqualTo(entity.getOfficeCode());
    assertThat(domain.applicationContent()).isEqualTo(entity.getApplicationContent());
    assertThat(domain.schemaVersion()).isEqualTo(entity.getSchemaVersion());
    assertThat(domain.createdAt()).isEqualTo(entity.getCreatedAt());
    assertThat(domain.modifiedAt()).isEqualTo(entity.getModifiedAt());
    assertThat(domain.applyApplicationId()).isEqualTo(entity.getApplyApplicationId());
    assertThat(domain.submittedAt()).isEqualTo(entity.getSubmittedAt());
    assertThat(domain.usedDelegatedFunctions()).isEqualTo(entity.getUsedDelegatedFunctions());
    assertThat(domain.categoryOfLaw()).isEqualTo(entity.getCategoryOfLaw().name());
    assertThat(domain.matterType()).isEqualTo(entity.getMatterType().name());
    assertThat(domain.isAutoGranted()).isEqualTo(entity.getIsAutoGranted());
    assertThat(domain.individuals()).hasSize(1);
    assertThat(domain.proceedings()).isEmpty();
  }

  @Test
  void toApplicationEntity_mapsAllFieldsFromDomain_insertPath() {
    ApplicationDomain domain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class,
            b -> b.id(null).createdAt(null)); // simulate pre-save state

    ApplicationEntity entity = mapper.toApplicationEntity(domain);

    assertThat(entity.getId()).isNull();
    assertThat(entity.getStatus().name()).isEqualTo(domain.status());
    assertThat(entity.getLaaReference()).isEqualTo(domain.laaReference());
    assertThat(entity.getOfficeCode()).isEqualTo(domain.officeCode());
    assertThat(entity.getSchemaVersion()).isEqualTo(domain.schemaVersion());
    assertThat(entity.getApplicationContent()).isEqualTo(domain.applicationContent());
    assertThat(entity.getApplyApplicationId()).isEqualTo(domain.applyApplicationId());
    assertThat(entity.getSubmittedAt()).isEqualTo(domain.submittedAt());
    assertThat(entity.getUsedDelegatedFunctions()).isEqualTo(domain.usedDelegatedFunctions());
    assertThat(entity.getCategoryOfLaw().name()).isEqualTo(domain.categoryOfLaw());
    assertThat(entity.getMatterType().name()).isEqualTo(domain.matterType());
    assertThat(entity.getIsAutoGranted()).isEqualTo(domain.isAutoGranted());
    assertThat(entity.getIndividuals()).hasSize(1);
    assertThat(entity.getProceedings()).hasSize(1);
  }

  // ── toEntity: null optional fields ──────────────────────────────────────

  @Test
  void toApplicationEntity_withNullCategoryOfLawAndMatterType_mapsNulls() {
    ApplicationDomain domain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class, b -> b.categoryOfLaw(null).matterType(null));

    ApplicationEntity entity = mapper.toApplicationEntity(domain);

    assertThat(entity.getCategoryOfLaw()).isNull();
    assertThat(entity.getMatterType()).isNull();
  }

  @Test
  void toApplicationEntity_withNullIndividuals_doesNotPopulateIndividuals() {
    ApplicationDomain domain =
        DataGenerator.createDefault(ApplicationDomainGenerator.class, b -> b.individuals(null));

    ApplicationEntity entity = mapper.toApplicationEntity(domain);

    assertThat(entity.getIndividuals()).isNull();
  }

  @Test
  void toApplicationEntity_withNullProceedings_doesNotPopulateProceedings() {
    ApplicationDomain domain =
        DataGenerator.createDefault(ApplicationDomainGenerator.class, b -> b.proceedings(null));

    ApplicationEntity entity = mapper.toApplicationEntity(domain);

    assertThat(entity.getProceedings()).isNull();
  }

  @Test
  void toApplicationEntity_withEmptyProceedings_doesNotPopulateProceedings() {
    ApplicationDomain domain =
        DataGenerator.createDefault(ApplicationDomainGenerator.class, b -> b.proceedings(Set.of()));

    ApplicationEntity entity = mapper.toApplicationEntity(domain);

    assertThat(entity.getProceedings()).isNull();
  }

  // ── toEntity: toIndividualEntity private method ──────────────────────────

  @Test
  void toApplicationEntity_mapsIndividualFields() {
    IndividualDomain individualDomain =
        DataGenerator.createDefault(IndividualDomainGenerator.class);
    ApplicationDomain domain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class, b -> b.individuals(Set.of(individualDomain)));

    ApplicationEntity entity = mapper.toApplicationEntity(domain);

    IndividualEntity individual = entity.getIndividuals().iterator().next();
    assertThat(individual.getFirstName()).isEqualTo(individualDomain.firstName());
    assertThat(individual.getLastName()).isEqualTo(individualDomain.lastName());
    assertThat(individual.getDateOfBirth()).isEqualTo(individualDomain.dateOfBirth());
    assertThat(individual.getIndividualContent()).isEqualTo(individualDomain.individualContent());
    assertThat(individual.getType().name()).isEqualTo(individualDomain.type());
  }

  @Test
  void toApplicationEntity_withNullIndividualType_mapsNullType() {
    IndividualDomain individualDomain =
        DataGenerator.createDefault(IndividualDomainGenerator.class, b -> b.type(null));
    ApplicationDomain domain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class, b -> b.individuals(Set.of(individualDomain)));

    ApplicationEntity entity = mapper.toApplicationEntity(domain);

    IndividualEntity individual = entity.getIndividuals().iterator().next();
    assertThat(individual.getType()).isNull();
  }

  // ── toEntity: toProceedingEntity private method ──────────────────────────

  @Test
  void toApplicationEntity_mapsProceedingFields() {
    ProceedingDomain proceedingDomain =
        DataGenerator.createDefault(ProceedingDomainGenerator.class);
    ApplicationDomain domain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class, b -> b.proceedings(Set.of(proceedingDomain)));

    ApplicationEntity entity = mapper.toApplicationEntity(domain);

    ProceedingEntity proceeding = entity.getProceedings().iterator().next();
    assertThat(proceeding.getApplyProceedingId()).isEqualTo(proceedingDomain.applyProceedingId());
    assertThat(proceeding.isLead()).isEqualTo(proceedingDomain.isLead());
    assertThat(proceeding.getDescription()).isEqualTo(proceedingDomain.description());
    assertThat(proceeding.getProceedingContent()).isEqualTo(proceedingDomain.proceedingContent());
    assertThat(proceeding.getCreatedBy()).isEqualTo(proceedingDomain.createdBy());
    assertThat(proceeding.getUpdatedBy()).isEqualTo(proceedingDomain.updatedBy());
  }

  @Test
  void toEntity_withNullProceedingCreatedByAndUpdatedBy_defaultsToApplicationEmptyString() {
    ProceedingDomain proceedingDomain =
        DataGenerator.createDefault(
            ProceedingDomainGenerator.class, b -> b.createdBy(null).updatedBy(null));
    ApplicationDomain domain =
        DataGenerator.createDefault(
            ApplicationDomainGenerator.class, b -> b.proceedings(Set.of(proceedingDomain)));

    ApplicationEntity entity = mapper.toApplicationEntity(domain);

    ProceedingEntity proceeding = entity.getProceedings().iterator().next();
    assertThat(proceeding.getCreatedBy()).isEmpty();
    assertThat(proceeding.getUpdatedBy()).isEmpty();
  }

  // ── toDomain: null optional fields ──────────────────────────────────────

  @Test
  void toApplicationDomain_withNullStatus_mapsNullStatus() {
    ApplicationEntity entity = ApplicationEntity.builder().status(null).build();

    ApplicationDomain domain = mapper.toApplicationDomain(entity);

    assertThat(domain.status()).isNull();
  }

  @Test
  void toApplicationDomain_withNullCategoryOfLawAndMatterType_mapsNulls() {
    ApplicationEntity entity =
        ApplicationEntity.builder()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .categoryOfLaw(null)
            .matterType(null)
            .build();

    ApplicationDomain domain = mapper.toApplicationDomain(entity);

    assertThat(domain.categoryOfLaw()).isNull();
    assertThat(domain.matterType()).isNull();
  }

  @Test
  void toApplicationDomain_withNullIndividuals_returnsEmptySet() {
    ApplicationEntity entity =
        ApplicationEntity.builder()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .individuals(null)
            .build();

    ApplicationDomain domain = mapper.toApplicationDomain(entity);

    assertThat(domain.individuals()).isEmpty();
  }

  @Test
  void toApplicationDomain_withNullProceedings_returnsEmptySet() {
    ApplicationEntity entity =
        ApplicationEntity.builder()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .proceedings(null)
            .build();

    ApplicationDomain domain = mapper.toApplicationDomain(entity);

    assertThat(domain.proceedings()).isEmpty();
  }

  // ── toDomain: toIndividualDomain private method ──────────────────────────

  @Test
  void toApplicationDomain_mapsIndividualFields() {
    UUID individualId = UUID.randomUUID();
    IndividualEntity individualEntity =
        DataGenerator.createDefault(IndividualEntityGenerator.class, b -> b.id(individualId));
    ApplicationEntity entity =
        ApplicationEntity.builder()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .individuals(Set.of(individualEntity))
            .build();

    ApplicationDomain domain = mapper.toApplicationDomain(entity);

    IndividualDomain individual = domain.individuals().iterator().next();
    assertThat(individual.id()).isEqualTo(individualId);
    assertThat(individual.firstName()).isEqualTo(individualEntity.getFirstName());
    assertThat(individual.lastName()).isEqualTo(individualEntity.getLastName());
    assertThat(individual.dateOfBirth()).isEqualTo(individualEntity.getDateOfBirth());
    assertThat(individual.individualContent()).isEqualTo(individualEntity.getIndividualContent());
    assertThat(individual.type()).isEqualTo(individualEntity.getType().name());
  }

  @Test
  void toApplicationDomain_withNullIndividualType_mapsNullType() {
    IndividualEntity individualEntity =
        DataGenerator.createDefault(IndividualEntityGenerator.class, b -> b.type(null));
    ApplicationEntity entity =
        ApplicationEntity.builder()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .individuals(Set.of(individualEntity))
            .build();

    ApplicationDomain domain = mapper.toApplicationDomain(entity);

    IndividualDomain individual = domain.individuals().iterator().next();
    assertThat(individual.type()).isNull();
  }

  // ── toDomain: toProceedingDomain private method ─────────────────────────

  @Test
  void toApplicationDomain_mapsProceedingFields() {
    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(ProceedingsEntityGenerator.class);
    ApplicationEntity entity =
        ApplicationEntity.builder()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .proceedings(Set.of(proceedingEntity))
            .build();

    ApplicationDomain domain = mapper.toApplicationDomain(entity);

    ProceedingDomain proceeding = domain.proceedings().iterator().next();
    assertThat(proceeding.id()).isEqualTo(proceedingEntity.getId());
    assertThat(proceeding.applyProceedingId()).isEqualTo(proceedingEntity.getApplyProceedingId());
    assertThat(proceeding.description()).isEqualTo(proceedingEntity.getDescription());
    assertThat(proceeding.isLead()).isEqualTo(proceedingEntity.isLead());
    assertThat(proceeding.proceedingContent()).isEqualTo(proceedingEntity.getProceedingContent());
    assertThat(proceeding.createdBy()).isEqualTo(proceedingEntity.getCreatedBy());
    assertThat(proceeding.updatedBy()).isEqualTo(proceedingEntity.getUpdatedBy());
    assertThat(proceeding.meritsDecision()).isNull();
  }

  @Test
  void toApplicationDomain_withProceedingWithMeritsDecision_mapsMeritsDecisionFields() {
    MeritsDecisionEntity meritsDecisionEntity = new MeritsDecisionEntity();
    meritsDecisionEntity.setDecision(
        uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus.GRANTED);
    meritsDecisionEntity.setReason("a reason");
    meritsDecisionEntity.setJustification("a justification");
    meritsDecisionEntity.setModifiedAt(Instant.now());

    ProceedingEntity proceedingEntity =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class, b -> b.meritsDecision(meritsDecisionEntity));
    ApplicationEntity entity =
        ApplicationEntity.builder()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .proceedings(Set.of(proceedingEntity))
            .build();

    ApplicationDomain domain = mapper.toApplicationDomain(entity);

    MeritsDecisionDomain meritsDecision = domain.proceedings().iterator().next().meritsDecision();
    assertThat(meritsDecision).isNotNull();
    assertThat(meritsDecision.decision()).isEqualTo(MeritsDecisionStatus.GRANTED);
    assertThat(meritsDecision.reason()).isEqualTo("a reason");
    assertThat(meritsDecision.justification()).isEqualTo("a justification");
  }

  // ── toDecisionDomain ─────────────────────────────────────────────────────

  @Test
  void toDecisionDomain_withNullInput_returnsNull() {
    assertThat(mapper.toDecisionDomain(null)).isNull();
  }

  @Test
  void toDecisionDomain_withNullOverallDecision_mapsNullOverallDecision() {
    DecisionEntity entity = DecisionEntity.builder().overallDecision(null).build();

    DecisionDomain domain = mapper.toDecisionDomain(entity);

    assertThat(domain.overallDecision()).isNull();
  }

  @Test
  void toDecisionDomain_mapsAllFields() {
    Instant now = Instant.now();
    DecisionEntity entity =
        DecisionEntity.builder()
            .overallDecision(uk.gov.justice.laa.dstew.access.model.DecisionStatus.GRANTED)
            .modifiedAt(now)
            .build();

    DecisionDomain domain = mapper.toDecisionDomain(entity);

    assertThat(domain.overallDecision()).isEqualTo(DecisionStatus.GRANTED);
    assertThat(domain.modifiedAt()).isEqualTo(now);
  }

  @Test
  void toApplicationDomain_withDecision_mapsDecisionDomain() {
    DecisionEntity decisionEntity =
        DecisionEntity.builder()
            .overallDecision(uk.gov.justice.laa.dstew.access.model.DecisionStatus.REFUSED)
            .build();
    ApplicationEntity entity =
        ApplicationEntity.builder()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .decision(decisionEntity)
            .build();

    ApplicationDomain domain = mapper.toApplicationDomain(entity);

    assertThat(domain.decision()).isNotNull();
    assertThat(domain.decision().overallDecision()).isEqualTo(DecisionStatus.REFUSED);
  }

  @Test
  void toApplicationDomain_withNullDecision_mapsNullDecision() {
    ApplicationEntity entity =
        ApplicationEntity.builder()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .decision(null)
            .build();

    ApplicationDomain domain = mapper.toApplicationDomain(entity);

    assertThat(domain.decision()).isNull();
  }

  // ── toMeritsDecisionDomain ───────────────────────────────────────────────

  @Test
  void toMeritsDecisionDomain_withNullInput_returnsNull() {
    assertThat(mapper.toMeritsDecisionDomain(null)).isNull();
  }

  @Test
  void toMeritsDecisionDomain_withNullDecision_mapsNullDecision() {
    MeritsDecisionEntity entity = new MeritsDecisionEntity();
    entity.setDecision(null);

    MeritsDecisionDomain domain = mapper.toMeritsDecisionDomain(entity);

    assertThat(domain.decision()).isNull();
  }

  @Test
  void toMeritsDecisionDomain_mapsAllFields() {
    Instant now = Instant.now();
    MeritsDecisionEntity entity = new MeritsDecisionEntity();
    entity.setDecision(uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus.GRANTED);
    entity.setReason("reason");
    entity.setJustification("justification");
    entity.setModifiedAt(now);

    MeritsDecisionDomain domain = mapper.toMeritsDecisionDomain(entity);

    assertThat(domain.decision()).isEqualTo(MeritsDecisionStatus.GRANTED);
    assertThat(domain.reason()).isEqualTo("reason");
    assertThat(domain.justification()).isEqualTo("justification");
    assertThat(domain.modifiedAt()).isEqualTo(now);
  }
}

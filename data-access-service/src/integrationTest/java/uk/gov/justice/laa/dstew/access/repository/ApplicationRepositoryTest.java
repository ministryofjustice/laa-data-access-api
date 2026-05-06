package uk.gov.justice.laa.dstew.access.repository;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.MeritsDecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;

public class ApplicationRepositoryTest extends BaseIntegrationTest {

  @Test
  public void givenSaveOfExpectedApplication_whenGetCalled_expectedAndActualAreEqual() {

    // given
    // Build full aggregate: meritsDecision → proceeding → application + decision
    MeritsDecisionEntity meritsDecision =
        DataGenerator.createDefault(MeritsDecisionsEntityGenerator.class);

    ProceedingEntity proceeding =
        DataGenerator.createDefault(
            ProceedingsEntityGenerator.class, builder -> builder.meritsDecision(meritsDecision));

    DecisionEntity expectedDecision = DataGenerator.createDefault(DecisionEntityGenerator.class);

    ApplicationEntity expected =
        persistedDataGenerator.createAndPersist(
            ApplicationEntityGenerator.class,
            builder ->
                builder
                    .caseworker(BaseIntegrationTest.CaseworkerJohnDoe)
                    .linkedApplications(Set.of())
                    .proceedings(new HashSet<>(Set.of(proceeding)))
                    .decision(expectedDecision));
    clearCache();

    // when
    ApplicationEntity actual = applicationRepository.findById(expected.getId()).orElse(null);

    // then
    assertApplicationEqual(expected, actual);
  }

  @Test
  public void
      givenSaveOfLinkedApplication_whenGetCalledOnLead_expectApplicationToHaveLinkedApplications() {
    // given
    final ApplicationEntity leadApplication =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
    final ApplicationEntity associatedApplication =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    persistedDataGenerator.persistLink(leadApplication, associatedApplication);
    clearCache();

    // when
    final ApplicationEntity actual =
        applicationRepository.findById(leadApplication.getId()).orElseThrow();

    // then
    assertThat(actual.getLinkedApplications()).isNotNull();
    assertThat(actual.getLinkedApplications().size()).isEqualTo(1);
    Set<UUID> linkedIds = actual.getLinkedApplicationIds();
    assertThat(linkedIds.contains(associatedApplication.getId())).isTrue();
    assertThat(actual.isLead()).isTrue();
  }

  private void assertApplicationEqual(ApplicationEntity expected, ApplicationEntity actual) {
    assertThat(expected)
        .usingRecursiveComparison()
        .ignoringFields("createdAt", "modifiedAt", "individuals", "proceedings")
        .isEqualTo(actual);
    assertThat(expected.getModifiedAt()).isNotNull();
  }
}

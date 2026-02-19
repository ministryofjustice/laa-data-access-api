package uk.gov.justice.laa.dstew.access.repository;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.DecisionEntity;
import uk.gov.justice.laa.dstew.access.entity.ProceedingEntity;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.decision.DecisionEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.merit.MeritsDecisionsEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.proceeding.ProceedingsEntityGenerator;

import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.List;

public class ApplicationRepositoryTest extends BaseIntegrationTest {
  @Test
  public void givenSaveOfExpectedApplication_whenGetCalled_expectedAndActualAreEqual() {

    // given
    ApplicationEntity expected = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class, builder ->
        builder.caseworker(BaseIntegrationTest.CaseworkerJohnDoe).linkedApplications(Set.of()));
    ProceedingEntity proceeding = persistedDataGenerator.createAndPersist(ProceedingsEntityGenerator.class, builder -> {
        builder.applicationId(expected.getId());
    });
    DecisionEntity expectedDecision = persistedDataGenerator.createAndPersist(DecisionEntityGenerator.class, builder -> {
        builder.meritsDecisions(Set.of(DataGenerator.createDefault(MeritsDecisionsEntityGenerator.class, mBuilder -> {
                mBuilder.proceeding(proceeding);
        })));
    });
    expected.setDecision(expectedDecision);
    applicationRepository.saveAndFlush(expected);
    clearCache();

    // when
    ApplicationEntity actual = applicationRepository.findById(expected.getId()).orElse(null);

    // then
    assertApplicationEqual(expected, actual);
  }

  @Test
  public void givenSaveOfLinkedApplication_whenGetCalledOnLead_expectApplicationToHaveLinkedApplications() {
    // given
    final ApplicationEntity leadApplication = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
    final ApplicationEntity associatedApplication = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
    clearCache();
    leadApplication.setLinkedApplications(Set.of(associatedApplication));
    applicationRepository.save(leadApplication);
    clearCache();

    //when
    final ApplicationEntity actual = applicationRepository.findById(leadApplication.getId()).orElseThrow();
    final ApplicationEntity actualAssociatedApplication = applicationRepository.findById(associatedApplication.getId()).orElseThrow();
    // then
    assertThat(actual.getLinkedApplications()).isNotNull();
    assertThat(actual.getLinkedApplications().size()).isEqualTo(1);
    assertApplicationEqual(actualAssociatedApplication, actual.getLinkedApplications().stream().findFirst().orElseThrow());
  }

  private void assertApplicationEqual(ApplicationEntity expected, ApplicationEntity actual) {
    assertThat(expected)
        .usingRecursiveComparison()
        .ignoringFields("createdAt", "modifiedAt")
        .isEqualTo(actual);
    assertThat(expected.getCreatedAt()).isNotNull();
    assertThat(expected.getModifiedAt()).isNotNull();
  }
}

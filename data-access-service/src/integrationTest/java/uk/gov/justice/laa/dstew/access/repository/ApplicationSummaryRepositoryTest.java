package uk.gov.justice.laa.dstew.access.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.LinkedApplicationSummaryDto;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ApplicationSummaryRepositoryTest extends BaseIntegrationTest {
  @Test 
  void givenLeadApplicationWithAssociates_whenGetAssociates_thenReturnAssociatedApplications() {
    // given
    final ApplicationEntity leadApplication = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
    final ApplicationEntity associatedApplication = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
    final ApplicationEntity associatedApplication2 = persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
    clearCache();
    leadApplication.setLinkedApplications(Set.of(associatedApplication, associatedApplication2));
    applicationRepository.save(leadApplication);
    clearCache();

    //when
    UUID[] ids = new UUID[] { leadApplication.getId() };
    final List<LinkedApplicationSummaryDto> actual = applicationRepository.findAssociateApplications(ids);

    //then
    assertThat(actual).isNotNull();
    assertThat(actual.size()).isEqualTo(2);

    List<UUID> associateIds = actual.stream().map(x -> x.getAssociateApplicationId()).toList();
    assertThat(associateIds.contains(associatedApplication.getId())).isTrue();
    assertThat(associateIds.contains(associatedApplication2.getId())).isTrue();
  }
}

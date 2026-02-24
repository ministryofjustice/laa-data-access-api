package uk.gov.justice.laa.dstew.access.repository;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.PocAppEntity;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.List;

public class PocApplicationRepositoryTest extends BaseIntegrationTest {
  @Test
  void showLinkedApplications() {
    //Given
    final PocAppEntity leadApp = PocAppEntity.builder().reference("lead").build();
    pocRepository.save(leadApp);
    clearCache();
    final PocAppEntity associate1 = PocAppEntity.builder().reference("associate1").leadApplicationId(leadApp.getId()).build();
    final PocAppEntity associate2 = PocAppEntity.builder().reference("associate2").leadApplicationId(leadApp.getId()).build();
    pocRepository.save(associate1);
    pocRepository.save(associate2);
    clearCache();

    //When
    final PocAppEntity getEntity = pocRepository.findById(leadApp.getId()).orElseThrow();

    //Then
    assertThat(getEntity).isNotNull();
    assertThat(getEntity.getLinkedApplications().size()).isEqualTo(2);

    final PocAppEntity getAssociatedApp = pocRepository.findById(associate1.getId()).orElseThrow();
    assertThat(getAssociatedApp).isNotNull();
    assertThat(getAssociatedApp.getLeadApplication()).isNotNull();
    assertThat(getAssociatedApp.getLinkedApplications().size()).isEqualTo(2);
  }
}

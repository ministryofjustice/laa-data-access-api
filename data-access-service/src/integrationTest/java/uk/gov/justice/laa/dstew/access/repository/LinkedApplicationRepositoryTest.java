package uk.gov.justice.laa.dstew.access.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.entity.LinkedApplicationEntity;
import uk.gov.justice.laa.dstew.access.utils.BaseIntegrationTest;

class LinkedApplicationRepositoryTest extends BaseIntegrationTest {

  @Test
  void givenLinkedApplicationEntity_whenSaved_thenPersistedCorrectly() {

    ApplicationEntity leadApplication =
        persistedApplicationFactory.createAndPersist(builder -> builder.build());

    ApplicationEntity associatedApplication =
        persistedApplicationFactory.createAndPersist(builder -> builder.build());

    Instant beforePersist = Instant.now().minusSeconds(1);

    LinkedApplicationEntity expected =
        persistedLinkedApplicationFactory.createAndPersist(builder ->
            builder
                .leadApplicationId(leadApplication.getId())
                .associatedApplicationId(associatedApplication.getId())
        );

    // when
    LinkedApplicationEntity actual =
        linkedApplicationRepository.findById(expected.getId()).orElseThrow();

    // then
    assertThat(actual.getId()).isNotNull();
    assertThat(actual.getLeadApplicationId()).isEqualTo(leadApplication.getId());
    assertThat(actual.getAssociatedApplicationId()).isEqualTo(associatedApplication.getId());
    assertThat(actual.getLinkedAt()).isNotNull().isAfterOrEqualTo(beforePersist);
  }
}


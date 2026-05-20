package uk.gov.justice.laa.dstew.access.service.applications.sharedAsserts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;

public class Application {

  public static void verifyThatApplicationEntitySaved(
      ApplicationRepository applicationRepository,
      ApplicationEntity expectedApplicationEntity,
      int timesCalled) {
    ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
    verify(applicationRepository, times(timesCalled)).save(captor.capture());
    ApplicationEntity actualApplicationEntity = captor.getValue();
    assertThat(expectedApplicationEntity)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .ignoringFields("createdAt", "modifiedAt")
        .isEqualTo(actualApplicationEntity);
    assertThat(actualApplicationEntity.getCreatedAt()).isNotNull();
    assertThat(actualApplicationEntity.getModifiedAt()).isNotNull();
  }
}

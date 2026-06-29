package uk.gov.justice.laa.dstew.access.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;

class ApplicationMapperTest {

  private final ApplicationMapper applicationMapper = new ApplicationMapperImpl();

  @Test
  void givenUpdateRequestWithStatus_whenUpdated_thenStatusAndContentAreSet() {
    ApplicationEntity applicationEntity =
        ApplicationEntity.builder().status(ApplicationStatus.APPLICATION_SUBMITTED).build();
    ApplicationUpdateRequest applicationUpdateRequest =
        new ApplicationUpdateRequest()
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .applicationContent(Map.of("key", "value"));

    applicationMapper.updateApplicationEntity(applicationEntity, applicationUpdateRequest);

    assertThat(applicationEntity.getStatus()).isEqualTo(ApplicationStatus.APPLICATION_IN_PROGRESS);
    assertThat(applicationEntity.getApplicationContent()).containsEntry("key", "value");
  }

  @Test
  void givenUpdateRequestWithoutStatus_whenUpdated_thenStatusRemainsUnchanged() {
    ApplicationEntity applicationEntity =
        ApplicationEntity.builder().status(ApplicationStatus.APPLICATION_SUBMITTED).build();
    ApplicationUpdateRequest applicationUpdateRequest =
        new ApplicationUpdateRequest().applicationContent(Map.of("next", "content"));

    applicationMapper.updateApplicationEntity(applicationEntity, applicationUpdateRequest);

    assertThat(applicationEntity.getStatus()).isEqualTo(ApplicationStatus.APPLICATION_SUBMITTED);
    assertThat(applicationEntity.getApplicationContent()).containsEntry("next", "content");
  }
}

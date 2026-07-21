package uk.gov.justice.laa.dstew.access.query.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.testutils.ApplicationSubmittedEventFixture;

class ApplicationProjectionTest {

  private final ApplicationReadRepository repository = mock(ApplicationReadRepository.class);
  private final ApplicationProjection projection = new ApplicationProjection(repository);

  @Test
  void givenCreatedEvent_whenProjected_thenSavesCorrectReadModel() {
    UUID applyApplicationId = UUID.randomUUID();
    var event = ApplicationSubmittedEventFixture.applicationSubmittedEvent(applyApplicationId);

    projection.on(event);

    ArgumentCaptor<ApplicationReadModel> captor = forClass(ApplicationReadModel.class);
    verify(repository).save(captor.capture());
    ApplicationReadModel saved = captor.getValue();

    assertThat(saved.getApplyApplicationId()).isEqualTo(applyApplicationId);
    assertThat(saved.getStatus()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(saved.getLaaReference()).isEqualTo("LAA-123");
    assertThat(saved.getOfficeCode()).isEqualTo("1A001B");
    assertThat(saved.getSchemaVersion()).isEqualTo(1);
    assertThat(saved.getApplicationType()).isEqualTo("APPLY");
    assertThat(saved.getCreatedAt()).isEqualTo(event.occurredAt());
  }
}

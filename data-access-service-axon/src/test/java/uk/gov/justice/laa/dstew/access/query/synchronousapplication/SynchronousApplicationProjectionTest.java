package uk.gov.justice.laa.dstew.access.query.synchronousapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.testutils.SynchronousApplicationCreatedEventFixture;

class SynchronousApplicationProjectionTest {

  private final SynchronousApplicationReadRepository repository =
      mock(SynchronousApplicationReadRepository.class);
  private final SynchronousApplicationProjection projection =
      new SynchronousApplicationProjection(repository);

  @Test
  void givenCreatedEvent_whenProjected_thenSavesCorrectReadModel() {
    UUID applyApplicationId = UUID.randomUUID();
    var event =
        SynchronousApplicationCreatedEventFixture.synchronousApplicationCreatedEvent(
            applyApplicationId);

    projection.on(event);

    ArgumentCaptor<SynchronousApplicationReadModel> captor =
        forClass(SynchronousApplicationReadModel.class);
    verify(repository).save(captor.capture());
    SynchronousApplicationReadModel saved = captor.getValue();

    assertThat(saved.getApplyApplicationId()).isEqualTo(applyApplicationId);
    assertThat(saved.getStatus()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(saved.getLaaReference()).isEqualTo("LAA-123");
    assertThat(saved.getOfficeCode()).isEqualTo("1A001B");
    assertThat(saved.getSchemaVersion()).isEqualTo(1);
    assertThat(saved.getApplicationType()).isEqualTo("APPLY");
    assertThat(saved.getCreatedAt()).isEqualTo(event.occurredAt());
    assertThat(saved.getIndividuals()).hasSize(1);
    assertThat(saved.getProceedings()).hasSize(1);
  }
}

package uk.gov.justice.laa.dstew.access.query.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.testutils.SynchronousApplicationCreatedEventFixture;

class SubmissionProjectionTest {

  private final SubmissionRepository repository = mock(SubmissionRepository.class);
  private final SubmissionProjection projection = new SubmissionProjection(repository);

  @Test
  void givenCreatedEvent_whenProjected_thenStoresTypedPayloadKeyedByEvent() {
    UUID applyApplicationId = UUID.randomUUID();
    var event =
        SynchronousApplicationCreatedEventFixture.synchronousApplicationCreatedEvent(
            applyApplicationId);
    EventMessage<?> message = GenericEventMessage.asEventMessage(event);

    projection.on(event, message);

    ArgumentCaptor<SubmissionRecord> captor = forClass(SubmissionRecord.class);
    verify(repository).save(captor.capture());
    SubmissionRecord saved = captor.getValue();

    assertThat(saved.getEventId()).isEqualTo(UUID.fromString(message.getIdentifier()));
    assertThat(saved.getApplyApplicationId()).isEqualTo(applyApplicationId);
    assertThat(saved.getSubmissionType()).isEqualTo(SubmissionType.CIVIL_APPLICATION);
    assertThat(saved.getData()).isNotNull();
    assertThat(saved.getData().individuals()).hasSize(1);
    assertThat(saved.getData().proceedings()).hasSize(1);
    assertThat(saved.getCreatedAt()).isEqualTo(event.occurredAt());
  }
}

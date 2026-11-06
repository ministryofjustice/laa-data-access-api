package uk.gov.justice.laa.dstew.access.query.genesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;

@ExtendWith(MockitoExtension.class)
class GenesisStreamProjectionTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-11-06T10:00:00Z");

  @Mock private GenesisStreamRepository repository;
  @InjectMocks private GenesisStreamProjection projection;

  private final UUID previousLead = UUID.randomUUID();
  private final UUID newLead = UUID.randomUUID();
  private final LinkedApplicationGroupLeadChangedEvent event =
      new LinkedApplicationGroupLeadChangedEvent(
          UUID.randomUUID(), previousLead, newLead, 1L, OCCURRED_AT);
  private final GenericEventMessage message =
      new GenericEventMessage(
          "event-1",
          new MessageType("access", "LinkedApplicationGroupLeadChanged", "0.0.1"),
          event,
          Map.of(),
          Instant.parse("2026-11-06T10:00:05Z"));

  @Test
  void givenEventNamingTwoApplications_whenHandled_thenPointsToItFromBoth() {
    projection.on(event, message);

    var captor = ArgumentCaptor.forClass(GenesisStreamEntry.class);
    verify(repository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(GenesisStreamEntry::getGenesisId)
        .containsExactlyInAnyOrder(previousLead, newLead);
    assertThat(captor.getAllValues())
        .allSatisfy(
            entry -> {
              assertThat(entry.getSource()).isEqualTo(GenesisStreamProjection.LOCAL_SOURCE);
              assertThat(entry.getEventIdentifier()).isEqualTo("event-1");
              assertThat(entry.getEventName())
                  .isEqualTo("access.LinkedApplicationGroupLeadChanged");
              assertThat(entry.getOccurredAt()).isEqualTo(OCCURRED_AT);
            });
  }

  @Test
  void givenPointerAlreadyWritten_whenRedelivered_thenSkipsIt() {
    when(repository.existsByGenesisIdAndSourceAndEventIdentifier(
            previousLead, GenesisStreamProjection.LOCAL_SOURCE, "event-1"))
        .thenReturn(true);
    when(repository.existsByGenesisIdAndSourceAndEventIdentifier(
            newLead, GenesisStreamProjection.LOCAL_SOURCE, "event-1"))
        .thenReturn(false);

    projection.on(event, message);

    var captor = ArgumentCaptor.forClass(GenesisStreamEntry.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getGenesisId()).isEqualTo(newLead);
  }

  @Test
  void whenReset_thenClearsAllPointers() {
    projection.reset();

    verify(repository).deleteAllInBatch();
    verify(repository, never()).save(any());
  }
}

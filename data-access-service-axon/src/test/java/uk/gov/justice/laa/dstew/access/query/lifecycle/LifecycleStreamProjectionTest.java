package uk.gov.justice.laa.dstew.access.query.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;

@ExtendWith(MockitoExtension.class)
class LifecycleStreamProjectionTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-11-06T10:00:00Z");

  @Mock private LifecycleStreamRepository repository;
  @InjectMocks private LifecycleStreamProjection projection;

  private final UUID previousLead = UUID.randomUUID();
  private final UUID newLead = UUID.randomUUID();
  private final LinkedApplicationGroupLeadChangedEvent event =
      new LinkedApplicationGroupLeadChangedEvent(
          UUID.randomUUID(), previousLead, newLead, 1L, OCCURRED_AT);
  private final GenericEventMessage message = message(event);

  @Test
  void givenEventNamingTwoApplications_whenHandled_thenPointsToItFromBoth() {
    projection.on(event, message);

    var captor = ArgumentCaptor.forClass(LifecycleStreamEntry.class);
    verify(repository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(LifecycleStreamEntry::getGenesisId)
        .containsExactlyInAnyOrder(previousLead, newLead);
    assertThat(captor.getAllValues())
        .allSatisfy(
            entry -> {
              assertThat(entry.getSource()).isEqualTo(LifecycleStreamProjection.LOCAL_SOURCE);
              assertThat(entry.getEventIdentifier()).isEqualTo("event-1");
              assertThat(entry.getEventName())
                  .isEqualTo("access.LinkedApplicationGroupLeadChangedEvent");
              assertThat(entry.getOccurredAt()).isEqualTo(OCCURRED_AT);
            });
  }

  @Test
  void givenPointerAlreadyWritten_whenRedelivered_thenSkipsIt() {
    when(repository.existsByGenesisIdAndSourceAndEventIdentifier(
            previousLead, LifecycleStreamProjection.LOCAL_SOURCE, "event-1"))
        .thenReturn(true);
    when(repository.existsByGenesisIdAndSourceAndEventIdentifier(
            newLead, LifecycleStreamProjection.LOCAL_SOURCE, "event-1"))
        .thenReturn(false);

    projection.on(event, message);

    var captor = ArgumentCaptor.forClass(LifecycleStreamEntry.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getGenesisId()).isEqualTo(newLead);
  }

  @Test
  void whenReset_thenClearsAllPointers() {
    projection.reset();

    verify(repository).deleteAllInBatch();
    verify(repository, never()).save(any());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource({
    "uk.gov.justice.laa.dstew.access.command.GenesisIdsTest#events",
    "uk.gov.justice.laa.dstew.access.command.GenesisIdsTest#legacyEvents"
  })
  void givenEvent_whenHandled_thenPointsToItFromEachRootApplicationItNames(
      String name, GenesisScoped scopedEvent, List<UUID> expected) throws Exception {
    Method handler =
        LifecycleStreamProjection.class.getDeclaredMethod(
            "on", scopedEvent.getClass(), EventMessage.class);

    handler.invoke(projection, scopedEvent, message(scopedEvent));

    var captor = ArgumentCaptor.forClass(LifecycleStreamEntry.class);
    verify(repository, times(expected.size())).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(LifecycleStreamEntry::getGenesisId)
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  private static GenericEventMessage message(Object payload) {
    return new GenericEventMessage(
        "event-1",
        new MessageType("access", payload.getClass().getSimpleName(), "0.0.1"),
        payload,
        Map.of(),
        Instant.parse("2026-11-06T10:00:05Z"));
  }
}

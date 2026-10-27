package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.modelling.StateManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;

@ExtendWith(MockitoExtension.class)
class LinkedGroupMembersTest {
  @Mock private StateManager stateManager;
  @Mock private ProcessingContext context;
  @Mock private ApplicationAggregate firstAggregate;
  @Mock private ApplicationAggregate secondAggregate;
  @Mock private ApplicationState firstState;
  @Mock private ApplicationState secondState;

  @Test
  void givenUnorderedDuplicateIds_whenLoading_thenReturnsStatesInIdOrderOnceEach() {
    var lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    var higherId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    when(firstAggregate.getState()).thenReturn(firstState);
    when(secondAggregate.getState()).thenReturn(secondState);
    when(stateManager.loadEntity(ApplicationAggregate.class, lowerId, context))
        .thenReturn(CompletableFuture.completedFuture(firstAggregate));
    when(stateManager.loadEntity(ApplicationAggregate.class, higherId, context))
        .thenReturn(CompletableFuture.completedFuture(secondAggregate));

    var loaded =
        LinkedGroupMembers.load(stateManager, List.of(higherId, lowerId, higherId), context);

    assertThat(loaded.keySet()).containsExactly(lowerId, higherId);
    assertThat(loaded.values()).containsExactly(firstState, secondState);
    verify(stateManager).loadEntity(ApplicationAggregate.class, lowerId, context);
    verify(stateManager).loadEntity(ApplicationAggregate.class, higherId, context);
  }
}

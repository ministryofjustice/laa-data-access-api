package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class MakeApplicationLeadCommandHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-14T15:00:00Z");

  @Mock private ApplicationGroupRouteResolver routeResolver;
  @Mock private RetryingCommandDispatcher dispatcher;

  @InjectMocks private MakeApplicationLeadCommandHandler handler;

  @Captor private ArgumentCaptor<Object> dispatchedCommandCaptor;

  @Test
  void givenLinkedApplication_whenHandled_thenDispatchesLeadChangeToResolvedGroup() {
    var applicationId = UUID.randomUUID();
    var groupId = UUID.randomUUID();
    var command = new MakeApplicationLeadCommand(applicationId, 4, OCCURRED_AT);
    when(routeResolver.resolveGroupForMutation(applicationId)).thenReturn(groupId);

    handler.handle(command);

    verify(dispatcher).dispatch(dispatchedCommandCaptor.capture());
    assertThat(dispatchedCommandCaptor.getValue())
        .isEqualTo(new ChangeLinkedGroupLeadCommand(groupId, applicationId, 4, OCCURRED_AT));
  }

  @Test
  void givenRouteResolverFails_whenHandled_thenPropagatesAndDoesNotDispatch() {
    var applicationId = UUID.randomUUID();
    var command = new MakeApplicationLeadCommand(applicationId, 4, OCCURRED_AT);
    var exception = new ResourceNotFoundException("route not found");
    when(routeResolver.resolveGroupForMutation(applicationId)).thenThrow(exception);

    assertThatThrownBy(() -> handler.handle(command)).isSameAs(exception);

    verifyNoInteractions(dispatcher);
  }
}

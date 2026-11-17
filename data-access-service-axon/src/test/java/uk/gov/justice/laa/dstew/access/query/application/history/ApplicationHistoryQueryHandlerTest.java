package uk.gov.justice.laa.dstew.access.query.application.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.query.lifecycle.LifecycleStreamReader;
import uk.gov.justice.laa.dstew.access.query.lifecycle.MissingStoredEventException;

@ExtendWith(MockitoExtension.class)
class ApplicationHistoryQueryHandlerTest {

  private static final UUID APPLICATION = UUID.randomUUID();
  private static final Instant OCCURRED_AT = Instant.parse("2026-07-19T10:00:00Z");

  @Mock private LifecycleStreamReader lifecycleStream;
  @Mock private ApplicationHistoryMapper mapper;
  @Mock private ApplicationHistoryAssembler applicationHistoryAssembler;
  @Mock private PriorAuthorityHistoryAssembler priorAuthorityHistoryAssembler;

  private ApplicationHistoryQueryHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new ApplicationHistoryQueryHandler(
            lifecycleStream, mapper, applicationHistoryAssembler, priorAuthorityHistoryAssembler);
  }

  @Test
  void givenHistoryQuery_whenHandled_thenAssemblesOnlyRequestedApplicationEventTypes() {
    List<EventMessage> events = List.of(event("e1"), event("e2"), event("e3"));
    var created = row("APPLICATION_CREATED");
    var joined = row("APPLICATION_GROUP_JOINED");
    var left = row("APPLICATION_GROUP_LEFT");
    var priorAuthorityRow =
        PriorAuthorityHistoryRow.builder()
            .applicationId(APPLICATION)
            .eventType("PRIOR_AUTHORITY_SUBMITTED")
            .build();
    when(lifecycleStream.read(APPLICATION)).thenReturn(events);
    when(mapper.map(APPLICATION, events))
        .thenReturn(
            new ApplicationHistoryMapper.Rows(
                List.of(created, joined, left), List.of(priorAuthorityRow)));
    var applicationResults = List.<ApplicationHistoryEventResult>of();
    var priorAuthorityResults = List.<PriorAuthorityHistoryGroupResult>of();
    when(applicationHistoryAssembler.assemble(List.of(created, left)))
        .thenReturn(applicationResults);
    when(priorAuthorityHistoryAssembler.assemble(List.of(priorAuthorityRow)))
        .thenReturn(priorAuthorityResults);

    var result =
        handler.handle(
            new FindApplicationHistoryQuery(
                APPLICATION, List.of("APPLICATION_GROUP_LEFT", "APPLICATION_CREATED")));

    assertThat(result.applicationHistoryEvents()).isSameAs(applicationResults);
    assertThat(result.priorAuthorityHistoryGroups()).isSameAs(priorAuthorityResults);
  }

  @Test
  void givenNoRequestedEventTypes_whenHandled_thenStillAssemblesPriorAuthorityHistory() {
    var priorAuthorityRow =
        PriorAuthorityHistoryRow.builder()
            .applicationId(APPLICATION)
            .eventType("PRIOR_AUTHORITY_SUBMITTED")
            .build();
    when(lifecycleStream.read(APPLICATION)).thenReturn(List.of());
    when(mapper.map(APPLICATION, List.of()))
        .thenReturn(
            new ApplicationHistoryMapper.Rows(
                List.of(row("APPLICATION_CREATED")), List.of(priorAuthorityRow)));
    when(applicationHistoryAssembler.assemble(List.of())).thenReturn(List.of());
    var priorAuthorityResults =
        List.of(new PriorAuthorityHistoryGroupResult(UUID.randomUUID(), "EXPERT", List.of()));
    when(priorAuthorityHistoryAssembler.assemble(List.of(priorAuthorityRow)))
        .thenReturn(priorAuthorityResults);

    var result = handler.handle(new FindApplicationHistoryQuery(APPLICATION, List.of()));

    assertThat(result.applicationHistoryEvents()).isEmpty();
    assertThat(result.priorAuthorityHistoryGroups()).isEqualTo(priorAuthorityResults);
  }

  @Test
  void givenStreamPointsToMissingEvent_whenHandled_thenThrowsIntegrityException() {
    var missing = new MissingStoredEventException(APPLICATION, "missing-event");
    when(lifecycleStream.read(APPLICATION)).thenThrow(missing);

    assertThatThrownBy(
            () ->
                handler.handle(
                    new FindApplicationHistoryQuery(APPLICATION, List.of("APPLICATION_CREATED"))))
        .isInstanceOfSatisfying(
            ApplicationHistoryIntegrityException.class,
            exception -> {
              assertThat(exception.getApplicationId()).isEqualTo(APPLICATION);
              assertThat(exception.getPriorAuthorityId()).isNull();
              assertThat(exception.getReason()).isEqualTo(missing.getMessage());
            });
    verifyNoInteractions(mapper, applicationHistoryAssembler, priorAuthorityHistoryAssembler);
  }

  private static ApplicationHistoryRow row(String eventType) {
    return ApplicationHistoryRow.builder()
        .eventId(UUID.randomUUID().toString())
        .applicationId(APPLICATION)
        .eventType(eventType)
        .occurredAt(OCCURRED_AT)
        .build();
  }

  private static EventMessage event(String identifier) {
    return new GenericEventMessage(
        identifier, new MessageType(String.class), "payload", Map.of(), OCCURRED_AT);
  }
}

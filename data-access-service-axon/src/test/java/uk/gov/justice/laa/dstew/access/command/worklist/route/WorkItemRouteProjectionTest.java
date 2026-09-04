package uk.gov.justice.laa.dstew.access.command.worklist.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

class WorkItemRouteProjectionTest {
  private final WorkItemRouteRepository routes =
      org.mockito.Mockito.mock(WorkItemRouteRepository.class);
  private final WorkItemRouteProjection projection = new WorkItemRouteProjection(routes);

  @Test
  void createsStandaloneApplicationRouteWhenManualAssessmentIsRequired() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-03T10:00:00Z");

    projection.on(new ApplicationReadyForManualAssessmentEvent(applicationId, 1L, 1L, occurredAt));

    WorkItemRoute route = savedRoute();
    assertThat(route.getWorkItemType()).isEqualTo(WorkItemType.APPLICATION);
    assertThat(route.getWorkItemId()).isEqualTo(applicationId);
    assertThat(route.getRouteKind()).isEqualTo(WorkItemRouteKind.STANDALONE);
    assertThat(route.getGroupId()).isNull();
    assertThat(route.getMembershipVersion()).isZero();
    assertThat(route.getCreatedAt()).isEqualTo(occurredAt);
    assertThat(route.getUpdatedAt()).isEqualTo(occurredAt);
  }

  @Test
  void deletesApplicationRouteIdempotentlyWhenDecided() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDecisionMadeEvent event =
        new ApplicationDecisionMadeEvent(applicationId, 2L, 2L, "REFUSED", null, Instant.now());

    projection.on(event);
    projection.on(event);

    verify(routes, org.mockito.Mockito.times(2)).deleteById(applicationId);
  }

  @Test
  void createsStandalonePriorAuthorityRoute() {
    UUID submissionId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-03T10:00:00Z");

    projection.on(
        new PriorAuthorityCreatedEvent(
            submissionId, UUID.randomUUID(), 1L, "fingerprint", "DRAFT", 1, occurredAt));

    WorkItemRoute route = savedRoute();
    assertThat(route.getWorkItemType()).isEqualTo(WorkItemType.PRIOR_AUTHORITY);
    assertThat(route.getWorkItemId()).isEqualTo(submissionId);
    assertThat(route.getRouteKind()).isEqualTo(WorkItemRouteKind.STANDALONE);
    assertThat(route.getGroupId()).isNull();
    assertThat(route.getMembershipVersion()).isZero();
    assertThat(route.getCreatedAt()).isEqualTo(occurredAt);
    assertThat(route.getUpdatedAt()).isEqualTo(occurredAt);
  }

  private WorkItemRoute savedRoute() {
    ArgumentCaptor<WorkItemRoute> routeCaptor = ArgumentCaptor.forClass(WorkItemRoute.class);
    verify(routes).save(routeCaptor.capture());
    return routeCaptor.getValue();
  }
}

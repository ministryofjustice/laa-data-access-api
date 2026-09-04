package uk.gov.justice.laa.dstew.access.command.worklist.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

class WorkItemRouteResolverTest {
  @Test
  void returnsAnExistingStandaloneRoute() {
    WorkItemRouteRepository routes = org.mockito.Mockito.mock(WorkItemRouteRepository.class);
    WorkItemRouteResolver resolver = new WorkItemRouteResolver(routes);
    UUID id = UUID.randomUUID();
    WorkItemRoute route =
        new WorkItemRoute(
            WorkItemType.APPLICATION,
            id,
            WorkItemRouteKind.STANDALONE,
            null,
            0L,
            Instant.parse("2026-09-01T10:00:00Z"));
    when(routes.findByWorkItemId(id)).thenReturn(Optional.of(route));

    assertThat(resolver.resolveDirectRoute(id)).isSameAs(route);
  }

  @Test
  void rejectsMissingAndNonStandaloneRoutes() {
    WorkItemRouteRepository routes = org.mockito.Mockito.mock(WorkItemRouteRepository.class);
    WorkItemRouteResolver resolver = new WorkItemRouteResolver(routes);
    UUID missingId = UUID.randomUUID();
    UUID linkedId = UUID.randomUUID();
    when(routes.findByWorkItemId(missingId)).thenReturn(Optional.empty());
    when(routes.findByWorkItemId(linkedId))
        .thenReturn(
            Optional.of(
                new WorkItemRoute(
                    WorkItemType.APPLICATION,
                    linkedId,
                    WorkItemRouteKind.LINKED_GROUP,
                    UUID.randomUUID(),
                    1L,
                    Instant.parse("2026-09-01T10:00:00Z"))));

    assertThatThrownBy(() -> resolver.resolveDirectRoute(missingId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No work-item route found for " + missingId);
    assertThatThrownBy(() -> resolver.resolveDirectRoute(linkedId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No direct work-item route found for " + linkedId);
  }
}

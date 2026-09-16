package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Maintains durable write-side application-to-group routing in the command transaction. */
@Component
@RequiredArgsConstructor
@Namespace("application-group-route")
public class ApplicationGroupRouteProjection {
  private static final long SUBMISSION_DATA_VERSION = 0L;

  private final ApplicationGroupRouteRepository routes;
  private final ApplicationDataStore applicationDataStore;

  /** Inserts the initial standalone route unless duplicate delivery has already created one. */
  @EventHandler
  @Transactional
  public void on(ApplicationCreatedEvent event) {
    if (routes.findById(event.applicationId()).isPresent()) {
      return;
    }

    var applicationData = applicationDataStore.get(event.applicationId(), SUBMISSION_DATA_VERSION);
    var officeCode = applicationData.provider().getOfficeCode();

    routes.save(
        new ApplicationGroupRoute(
            event.applicationId(),
            ApplicationGroupRouteKind.STANDALONE,
            null,
            officeCode,
            event.occurredAt()));
  }

  /** Transitions all listed member routes into the created linked group inside one transaction. */
  @EventHandler
  @Transactional
  public void on(LinkedApplicationGroupCreatedEvent event) {
    var memberApplicationIds = event.memberApplicationIds().stream().distinct().sorted().toList();
    var lockedRoutes = routes.findAllByApplicationIdInForUpdate(memberApplicationIds);
    ensureAllRoutesPresent(memberApplicationIds, lockedRoutes);

    lockedRoutes.forEach(route -> route.join(event.groupId(), event.occurredAt()));
    routes.saveAll(lockedRoutes);
  }

  /** Transitions one existing standalone member route into the supplied linked group. */
  @EventHandler
  @Transactional
  public void on(MemberAddedToGroupEvent event) {
    var route =
        routes
            .findByApplicationIdForUpdate(event.memberId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No application group route found for application " + event.memberId()));

    route.join(event.groupId(), event.occurredAt());
    routes.save(route);
  }

  private void ensureAllRoutesPresent(
      List<UUID> memberApplicationIds, List<ApplicationGroupRoute> lockedRoutes) {
    Set<UUID> lockedApplicationIds =
        lockedRoutes.stream()
            .map(ApplicationGroupRoute::getApplicationId)
            .collect(Collectors.toSet());
    if (!lockedApplicationIds.equals(Set.copyOf(memberApplicationIds))) {
      throw new ResourceNotFoundException(
          "No application group routes found for applications " + memberApplicationIds);
    }
  }
}

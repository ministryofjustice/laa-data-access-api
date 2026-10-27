package uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.BaseIntegrationTest;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ChangeLinkedGroupLeadCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.DirectWorkItemAssignmentCommand;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkType;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.LinkedGroupChangeRequest;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignRequest;
import uk.gov.justice.laa.dstew.access.version.VersionToken;

class RouteLockGuardIntegrationTest extends BaseIntegrationTest {
  @Autowired private CommandGateway commandGateway;

  @Autowired private ApplicationGroupRouteRepository routes;

  @Test
  void givenNoRouteLocks_whenCommandDispatchedDirectly_thenGuardRejectsAndCoordinatorSucceeds() {
    UUID leadApplicationId = createReadyApplication();
    UUID associateApplicationId = createReadyApplication();
    var linkRequest =
        new ApplicationLinkRequest(associateApplicationId, ApplicationLinkType.FAMILY);
    ResponseEntity<Void> linkResponse =
        restTemplate.exchange(
            url("/api/v0/applications/" + leadApplicationId + "/link"),
            HttpMethod.POST,
            new HttpEntity<>(linkRequest, headers()),
            Void.class);
    assertThat(linkResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    UUID groupId =
        await()
            .atMost(15, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(
                () ->
                    routes
                        .findById(associateApplicationId)
                        .map(ApplicationGroupRoute::getGroupId)
                        .orElse(null),
                group -> group != null);

    assertThatThrownBy(
            () ->
                commandGateway.sendAndWait(
                    new ChangeLinkedGroupLeadCommand(
                        groupId, associateApplicationId, 0L, Instant.now())))
        .satisfies(
            throwable ->
                assertThat(causeChain(throwable))
                    .anySatisfy(
                        cause ->
                            assertThat(cause)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("requires linked-group route locks")));

    var changeRequest =
        new LinkedGroupChangeRequest(VersionToken.linkedGroup(groupId, 0L).encode());
    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/api/v0/applications/" + associateApplicationId + "/make-lead"),
            HttpMethod.POST,
            new HttpEntity<>(changeRequest, headers()),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void givenStandaloneApplication_whenDirectAssignmentDispatchedWithoutLock_thenRejected() {
    UUID applicationId = createReadyApplication();

    assertThatThrownBy(
            () ->
                commandGateway.sendAndWait(
                    new DirectWorkItemAssignmentCommand(
                        applicationId, UUID.randomUUID(), 0L, "{}", "Assigned", Instant.now())))
        .satisfies(
            throwable ->
                assertThat(causeChain(throwable))
                    .anySatisfy(
                        cause ->
                            assertThat(cause)
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining(
                                    "DirectWorkItemAssignmentCommand requires linked-group route locks")));

    ResponseEntity<Void> response =
        restTemplate.exchange(
            url("/api/v0/work-list/" + applicationId + "/assign"),
            HttpMethod.POST,
            new HttpEntity<>(new WorkListAssignRequest(0L), headers()),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private UUID createReadyApplication() {
    UUID applicationId = UUID.randomUUID();
    var request = validCreateApplicationRequest(applicationId, UUID.randomUUID());
    ResponseEntity<Void> created =
        restTemplate.postForEntity(
            url("/api/v0/applications"), new HttpEntity<>(request, headers()), Void.class);
    assertThat(created.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);

    ResponseEntity<Void> ready =
        restTemplate.exchange(
            url("/api/v0/applications/" + applicationId + "/auto-grant-outcome"),
            HttpMethod.PATCH,
            new HttpEntity<>(new ManualOutcomeRequest(AutoGrantOutcome.MANUAL), headers()),
            Void.class);
    assertThat(ready.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    return applicationId;
  }

  private List<Throwable> causeChain(Throwable throwable) {
    var causes = new ArrayList<Throwable>();
    for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
      causes.add(cause);
    }
    return causes;
  }
}

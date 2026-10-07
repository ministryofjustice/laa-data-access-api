package uk.gov.justice.laa.dstew.access.pact;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.justice.laa.dstew.access.DataAccessServiceAxonApplication;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCommand;
import uk.gov.justice.laa.dstew.access.command.application.ready.RecordAutoGrantOutcomeUseCase;
import uk.gov.justice.laa.dstew.access.controller.application.AutoGrantOutcomeCommandMapper;
import uk.gov.justice.laa.dstew.access.controller.application.CreateApplicationCommandMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsQuery;
import uk.gov.justice.laa.dstew.access.query.application.FindAllApplicationsResult;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsQuery;
import uk.gov.justice.laa.dstew.access.query.individual.FindIndividualsResult;

/**
 * Shared scaffolding for Pact provider verification.
 *
 * <p>Boots the real Axon application on a random port against in-memory H2 (see {@code
 * src/pactTest/resources/application-pact.yml}). Nothing below the controllers is mocked. Provider
 * states create data by dispatching real commands through the use cases, then wait until the
 * relevant projection can serve it, so the replayed request reads from a real read model.
 *
 * <p>Pact replays each interaction in isolation and calls the state handler first. The wait inside
 * the handler is what absorbs the projection lag of the event-sourced write path.
 */
@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("pact")
public abstract class AbstractProviderPactTests {

  private static final String STATE_SETUP_PRINCIPAL = "pact-provider-state-setup";
  private static final Duration PROJECTION_WAIT = Duration.ofSeconds(30);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

  @Autowired protected QueryGateway queryGateway;
  @Autowired protected CreateApplicationUseCase createApplicationUseCase;
  @Autowired protected RecordAutoGrantOutcomeUseCase recordAutoGrantOutcomeUseCase;
  @Autowired protected CreateApplicationCommandMapper createApplicationCommandMapper;
  @Autowired protected AutoGrantOutcomeCommandMapper autoGrantOutcomeCommandMapper;

  /**
   * State handlers call secured use cases directly, outside any HTTP request, so they need an
   * authenticated caseworker on the test thread. The replayed HTTP request authenticates separately
   * through the real security filter chain.
   */
  @BeforeEach
  void authenticateStateSetup() {
    TestingAuthenticationToken authentication =
        new TestingAuthenticationToken(
            STATE_SETUP_PRINCIPAL,
            "n/a",
            new SimpleGrantedAuthority("APPROLE_LAA_CASEWORKER"),
            new SimpleGrantedAuthority("ROLE_LAA_CASEWORKER"));
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @AfterEach
  void clearStateSetupAuthentication() {
    SecurityContextHolder.clearContext();
  }

  /**
   * Ensures the application exists, creating it through the real create path if it does not.
   *
   * <p>Idempotent: the in-memory database lives for the whole verification run, and several
   * interactions may share a state, so an existing application is left untouched.
   *
   * @return the application's current-state read model once the projection can serve it
   */
  protected ApplicationReadModel ensureApplicationExists(ApplicationCreateRequest request) {
    UUID applicationId = request.getId();
    ApplicationReadModel existing = findApplication(applicationId);
    if (existing != null) {
      return existing;
    }
    boolean projected =
        createApplicationUseCase.execute(createApplicationCommandMapper.toCommand(request, 1));
    if (!projected) {
      throw new IllegalStateException(
          "Projection for application " + applicationId + " did not become readable in time");
    }
    return awaitApplication(applicationId);
  }

  /**
   * Marks the application as needing manual assessment, so it appears in caseworker list views.
   * Idempotent: recording the outcome twice is a no-op in the aggregate.
   */
  protected void ensureReadyForManualAssessment(UUID applicationId) {
    ApplicationReadModel current = awaitApplication(applicationId);
    if (current.getAutoGranted() == AutoGrantedState.MANUAL) {
      return;
    }
    recordAutoGrantOutcomeUseCase.recordReady(
        (MarkApplicationReadyCommand)
            autoGrantOutcomeCommandMapper.toCommand(
                applicationId, new ManualOutcomeRequest(AutoGrantOutcome.MANUAL)));
    await()
        .atMost(PROJECTION_WAIT)
        .pollInterval(POLL_INTERVAL)
        .until(
            () -> findApplication(applicationId),
            application ->
                application != null && application.getAutoGranted() == AutoGrantedState.MANUAL);
  }

  /** Waits until the list index projection returns the application in an unfiltered list. */
  protected void awaitListed(UUID applicationId) {
    await()
        .atMost(PROJECTION_WAIT)
        .pollInterval(POLL_INTERVAL)
        .until(
            () ->
                queryGateway
                    .query(
                        new FindAllApplicationsQuery(
                            null, null, "KPBLW", null, null, null, null, null, null, 1, 100),
                        FindAllApplicationsResult.class)
                    .join(),
            result ->
                result != null
                    && result.applications().stream()
                        .anyMatch(app -> applicationId.equals(app.getApplicationId())));
  }

  /** Waits until the individuals query can return the application's client. */
  protected void awaitClientIndividual(UUID applicationId) {
    await()
        .atMost(PROJECTION_WAIT)
        .pollInterval(POLL_INTERVAL)
        .until(
            () ->
                queryGateway
                    .query(
                        new FindIndividualsQuery(applicationId, "CLIENT", false, 1, 10),
                        FindIndividualsResult.class)
                    .join(),
            result -> result != null && result.client() != null);
  }

  private ApplicationReadModel awaitApplication(UUID applicationId) {
    return await()
        .atMost(PROJECTION_WAIT)
        .pollInterval(POLL_INTERVAL)
        .until(() -> findApplication(applicationId), Objects::nonNull);
  }

  private ApplicationReadModel findApplication(UUID applicationId) {
    return queryGateway
        .query(new FindApplicationByIdQuery(applicationId), ApplicationReadModel.class)
        .join();
  }
}

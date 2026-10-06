package uk.gov.justice.laa.dstew.access.pact;

import static uk.gov.justice.laa.dstew.access.pact.PactApplicationFixtures.APPLICATION_001;
import static uk.gov.justice.laa.dstew.access.pact.PactApplicationFixtures.submittedApplication001;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import org.apache.hc.core5.http.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Pact provider verification entry point for {@code laa-data-access-api}.
 *
 * <p>Pulls every consumer pact registered against the provider name below from the broker, replays
 * each interaction against the real application on a random local port, and publishes the result
 * back when broker credentials are present (see the {@code pactTest} Gradle task).
 *
 * <p>Each {@code @State} method is the provider-side implementation of a state string a consumer
 * wrote in {@code .given("...")}. The literal must match the consumer's exactly, so agree changes
 * with the consumer team before renaming one.
 */
@Provider("laa-data-access-api")
@PactBroker
public class DataAccessApiProviderTests extends AbstractProviderPactTests {

  private static final Logger log = LoggerFactory.getLogger(DataAccessApiProviderTests.class);

  private static final String DEV_CASEWORKER_TOKEN = "Bearer swagger-caseworker-token";
  private static final String DEFAULT_SERVICE_NAME = "CIVIL_DECIDE";

  @LocalServerPort private int port;

  @BeforeEach
  void setTarget(PactVerificationContext context) {
    context.setTarget(new HttpTestTarget("localhost", port));
  }

  /**
   * Replays one interaction. The injected request is modified before it is sent, which is the JUnit
   * 5 equivalent of a request filter.
   *
   * <p>The bearer token is replaced so every replayed request authenticates through the real
   * security filter chain. Credentials are the one thing Pact's own guidance says may be
   * substituted at replay time, because a consumer cannot persist a valid token in its pact file.
   * The dev token maps to a caseworker with a fixed Entra OID in {@code SecurityConfig}.
   *
   * <p>{@code X-Service-Name} is only defaulted when absent: each consumer sends its own value and
   * that value is recorded against the data it writes.
   */
  @TestTemplate
  @ExtendWith(PactVerificationInvocationContextProvider.class)
  void pactVerificationTestTemplate(PactVerificationContext context, HttpRequest request) {
    request.setHeader("Authorization", DEV_CASEWORKER_TOKEN);
    if (!request.containsHeader("X-Service-Name")) {
      request.setHeader("X-Service-Name", DEFAULT_SERVICE_NAME);
    }
    context.verifyInteraction();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // GET /api/v0/applications
  // Consumer: laa-civil-decide-api
  // ═══════════════════════════════════════════════════════════════════════════

  /** One submitted application awaiting manual assessment, so it appears in the list view. */
  @State("applications exist")
  public void applicationsExist() {
    log.info("Setting up state: applications exist");
    ensureApplicationExists(submittedApplication001());
    ensureReadyForManualAssessment(APPLICATION_001);
    awaitListed(APPLICATION_001);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // GET /api/v0/individuals
  // Consumer: laa-civil-decide-api
  // ═══════════════════════════════════════════════════════════════════════════

  /** Application 001 exists with a client, so the individuals query can return that client. */
  @State("a client individual exists for application 00000000-0000-0000-0000-000000000001")
  public void clientIndividualExistsForApplication001() {
    log.info("Setting up state: a client individual exists for application {}", APPLICATION_001);
    ensureApplicationExists(submittedApplication001());
    awaitClientIndividual(APPLICATION_001);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // POST /api/v0/applications
  // Consumer: laa-apply-for-legal-aid
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Nothing to set up: the database starts empty for the run, and the consumer supplies its own
   * application ID in the request body, so the real create path succeeds and returns the Location.
   */
  @State("that no matching special children act application already exists")
  public void noMatchingSpecialChildrenActApplicationExists() {
    log.info("Setting up state: no matching special children act application exists");
  }
}

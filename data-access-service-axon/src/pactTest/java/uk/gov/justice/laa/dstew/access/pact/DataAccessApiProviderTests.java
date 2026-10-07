package uk.gov.justice.laa.dstew.access.pact;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.StateChangeAction;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import java.util.Map;
import org.apache.hc.core5.http.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Pact provider verification entry point for {@code laa-data-access-api}.
 *
 * <p>Pulls every consumer pact selected for this provider from the broker, replays each interaction
 * against the real application on a random local port, and publishes the result back when broker
 * credentials are present (see the {@code pactTest} Gradle task).
 *
 * <p>Each {@code @State} method maps a string a consumer wrote in {@code .given("...")} onto a
 * {@link ProviderStates} method. The strings are the contract between teams: see {@link PactStates}
 * and {@code docs/pact-provider-states.md}.
 */
@Provider("laa-data-access-api")
@PactBroker
public class DataAccessApiProviderTests extends AbstractProviderPactTests {

  private static final String DEV_CASEWORKER_TOKEN = "Bearer swagger-caseworker-token";

  @LocalServerPort private int port;

  @BeforeEach
  void setTarget(PactVerificationContext context) {
    context.setTarget(new HttpTestTarget("localhost", port));
  }

  /**
   * Replays one interaction. The injected request is modified before it is sent, which is the JUnit
   * 5 equivalent of a request filter.
   *
   * <p>Only the bearer token is replaced. Credentials are the one thing Pact's own guidance says
   * may be substituted at replay time, because a consumer cannot persist a valid token in its pact
   * file. The dev token maps to a caseworker with a fixed Entra OID in {@code SecurityConfig}.
   * Everything else, including {@code X-Service-Name}, is replayed exactly as the consumer wrote
   * it.
   */
  @TestTemplate
  @ExtendWith(PactVerificationInvocationContextProvider.class)
  void pactVerificationTestTemplate(PactVerificationContext context, HttpRequest request) {
    request.setHeader("Authorization", DEV_CASEWORKER_TOKEN);
    context.verifyInteraction();
  }

  // ─── Applications ────────────────────────────────────────────────────────────

  /** Consumer: laa-civil-decide-api, GET applications. */
  @State(PactStates.APPLICATIONS_EXIST)
  public void applicationsExist() {
    states.applicationsExist();
  }

  /** Consumer: laa-civil-decide-api, GET individuals. */
  @State(PactStates.CLIENT_INDIVIDUAL_EXISTS_FOR_APPLICATION_001)
  public void clientIndividualExistsForApplication001() {
    states.clientIndividualExistsForApplication001();
  }

  /** Consumer: laa-apply-for-legal-aid, POST applications. */
  @State(PactStates.NO_MATCHING_SCA_APPLICATION_EXISTS)
  public void noMatchingSpecialChildrenActApplicationExists() {
    states.noMatchingSpecialChildrenActApplicationExists();
  }

  /** GET by id, PATCH, history, auto-grant outcome. */
  @State(PactStates.APPLICATION_SUBMITTED_EXISTS)
  public void submittedApplicationExists() {
    states.submittedApplicationExists();
  }

  /** PATCH decision, work-list unassign. */
  @State(PactStates.APPLICATION_ASSIGNED_EXISTS)
  public void applicationAssignedExists() {
    states.applicationAssignedExists();
  }

  /** Work-list assign, list filtered by auto-grant outcome. */
  @State(PactStates.APPLICATION_UNASSIGNED_EXISTS)
  public void applicationUnassignedExists() {
    states.applicationUnassignedExists();
  }

  /** GET certificate, history with a decision. */
  @State(PactStates.APPLICATION_GRANTED_EXISTS)
  public void applicationGrantedExists() {
    states.applicationGrantedExists();
  }

  /** GET notes. */
  @State(PactStates.APPLICATION_WITH_NOTES_EXISTS)
  public void applicationWithNotesExists() {
    states.applicationWithNotesExists();
  }

  /** GET by id with a linked group, unlink, make-lead. */
  @State(PactStates.APPLICATIONS_LINKED)
  public void applicationsLinked() {
    states.applicationsLinked();
  }

  /** Any application endpoint expecting 404. */
  @State(PactStates.NO_APPLICATION_WITH_ID)
  public void noApplicationWithId() {
    states.noApplicationWithId();
  }

  /** POST applications expecting 202. */
  @State(PactStates.APPLICATION_READ_MODEL_LAGGING)
  public void applicationReadModelLagging() {
    states.applicationReadModelLagging();
  }

  /** Restores the projection after the 202 interaction. */
  @State(value = PactStates.APPLICATION_READ_MODEL_LAGGING, action = StateChangeAction.TEARDOWN)
  public void applicationReadModelCaughtUp() {
    states.applicationReadModelCaughtUp();
  }

  // ─── Work list ───────────────────────────────────────────────────────────────

  /** GET work-list. */
  @State(PactStates.WORK_LIST_ITEMS_EXIST)
  public void workListItemsExist() {
    states.workListItemsExist();
  }

  // ─── Prior authorities ───────────────────────────────────────────────────────

  /** GET draft, PUT draft, POST submit, POST documents. */
  @State(PactStates.PRIOR_AUTHORITY_DRAFT_EXISTS)
  public void priorAuthorityDraftExists() {
    states.priorAuthorityDraftExists();
  }

  /** GET submitted, work-list assign. */
  @State(PactStates.PRIOR_AUTHORITY_SUBMITTED_EXISTS)
  public void priorAuthoritySubmittedExists() {
    states.priorAuthoritySubmittedExists();
  }

  /** PATCH decision, work-list unassign. */
  @State(PactStates.PRIOR_AUTHORITY_ASSIGNED_EXISTS)
  public void priorAuthorityAssignedExists() {
    states.priorAuthorityAssignedExists();
  }

  /** GET decided, PATCH decision expecting 409. */
  @State(PactStates.PRIOR_AUTHORITY_DECIDED_EXISTS)
  public void priorAuthorityDecidedExists() {
    states.priorAuthorityDecidedExists();
  }

  /**
   * GET, PATCH and DELETE a document. Returns {@code documentId} for Pact to inject into the
   * consumer's request path, since the service generates document identifiers.
   */
  @State(PactStates.PRIOR_AUTHORITY_WITH_DOCUMENT_EXISTS)
  public Map<String, Object> priorAuthorityWithDocumentExists() {
    return states.priorAuthorityWithDocumentExists();
  }

  /** Any prior authority endpoint expecting 404. */
  @State(PactStates.NO_PRIOR_AUTHORITY_WITH_ID)
  public void noPriorAuthorityWithId() {
    states.noPriorAuthorityWithId();
  }
}

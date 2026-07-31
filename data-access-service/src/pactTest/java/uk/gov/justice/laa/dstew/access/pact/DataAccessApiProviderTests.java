package uk.gov.justice.laa.dstew.access.pact;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.TargetRequestFilter;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.justice.laa.dstew.access.domain.ApplicationClientDetailsDomain;
import uk.gov.justice.laa.dstew.access.domain.ApplicationDomain;
import uk.gov.justice.laa.dstew.access.domain.IndividualDomain;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.GetAllApplicationsResult;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.model.ApplicationSummaryReadModel;
import uk.gov.justice.laa.dstew.access.usecase.getallindividuals.GetAllIndividualsResult;
import uk.gov.justice.laa.dstew.access.usecase.shared.PagedResult;

/**
 * Pact provider verification entry point for {@code laa-data-access-api}.
 *
 * <p>Pulls every consumer pact registered against the provider name below from the broker, runs
 * each interaction against this Spring Boot app on a random local port, and (when broker creds are
 * set in env) publishes the verification result back.
 *
 * <p>Each {@code @State("...")} method on this class is the provider-side implementation of a state
 * string a consumer named via {@code .given("...")} when they generated their pact.
 *
 * <p>State strings live across repos: the literal string here must match the literal string the
 * consumer wrote. Agree these with the consumer team before changing.
 */
@Slf4j
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Provider("laa-data-access-api")
@PactBroker
public class DataAccessApiProviderTests extends AbstractProviderPactTests {

  private static final UUID CREATED_APPLICATION_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");

  @LocalServerPort private int port;

  @BeforeEach
  void setUp(PactVerificationContext context) {
    context.setTarget(new HttpTestTarget("localhost", port));

    // POST /api/v0/applications — stubbed unconditionally because a create interaction
    // may carry no provider state (nothing needs to pre-exist for a create).
    // The controller only reads .id() to build the Location header.
    when(createApplicationUseCase.execute(any()))
        .thenReturn(ApplicationDomain.builder().id(CREATED_APPLICATION_ID).build());
  }

  @TestTemplate
  @ExtendWith(PactVerificationInvocationContextProvider.class)
  void pactVerificationTestTemplate(PactVerificationContext context) {
    context.verifyInteraction();
  }

  /**
   * Ensures auth headers are present on every replayed request without clobbering what the
   * consumer's pact already carries: pacts replay their example header values, and each consumer
   * sends its own {@code X-Service-Name} (CIVIL_DECIDE, CIVIL_APPLY, ...), so values are only
   * injected when missing. Security itself is off ({@code feature.disable-security=true}).
   */
  @TargetRequestFilter
  public void requestFilter(HttpRequest request) {
    if (!request.containsHeader("Authorization")) {
      request.setHeader("Authorization", "Bearer swagger-caseworker-token");
    }
    if (!request.containsHeader("X-Service-Name")) {
      request.setHeader("X-Service-Name", "CIVIL_DECIDE");
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // GET /api/v0/applications — happy path
  // Consumer: laa-civil-decide-api
  // ═══════════════════════════════════════════════════════════════════════════

  @State("applications exist")
  public void applicationsExist() {
    log.info("Setting up state: applications exist");

    ApplicationSummaryReadModel sample =
        ApplicationSummaryReadModel.builder()
            .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            .laaReference("LAA-REF-0001")
            .status("APPLICATION_SUBMITTED")
            .applicationType("INITIAL")
            .categoryOfLaw("FAMILY")
            .matterType("SPECIAL_CHILDREN_ACT")
            .isLead(true)
            .isAutoGranted(false)
            .usedDelegatedFunctions(false)
            .submittedAt(Instant.parse("2026-01-15T10:00:00Z"))
            .modifiedAt(Instant.parse("2026-01-16T12:30:00Z"))
            .clientFirstName("Test")
            .clientLastName("Client")
            .clientDateOfBirth(LocalDate.parse("1980-01-01"))
            .officeCode("0A001D")
            .linkedApplications(List.of())
            .build();

    when(getAllApplicationsUseCase.execute(any()))
        .thenReturn(new GetAllApplicationsResult(new PagedResult<>(List.of(sample), 1L), 1, 10));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // GET /api/v0/individuals — happy path (client enrichment lookup)
  // Consumer: laa-civil-decide-api
  // ═══════════════════════════════════════════════════════════════════════════

  @State("a client individual exists for application 00000000-0000-0000-0000-000000000001")
  public void aClientIndividualExistsForApplication() {
    log.info("Setting up state: a client individual exists for application");

    IndividualDomain sample =
        IndividualDomain.builder()
            .id(UUID.fromString("00000000-0000-0000-0000-000000000099"))
            .firstName("Alice")
            .lastName("Anderson")
            .type("CLIENT")
            .build();

    // Non-null clientDetails makes the response mapper populate clientId from the
    // individual's id, which the consumer's pact asserts on.
    when(getAllIndividualsUseCase.execute(any()))
        .thenReturn(
            GetAllIndividualsResult.builder()
                .individuals(
                    new uk.gov.justice.laa.dstew.access.usecase.getallindividuals.PagedResult<>(
                        List.of(sample), 1L))
                .requestedPage(1)
                .requestedPageSize(10)
                .clientDetails(ApplicationClientDetailsDomain.builder().build())
                .build());
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // POST /api/v0/applications — happy path (create special children act application)
  // Consumer: laa-apply-for-legal-aid (spec/pact/providers/laa_data_access_api/
  // create_application_spec.rb)
  // ═══════════════════════════════════════════════════════════════════════════

  @State("that no matching special children act application already exists")
  public void noMatchingSpecialChildrenActApplicationExists() {
    log.info("Setting up state: no matching special children act application exists");

    // "No matching application" means the create succeeds: the use case (which owns
    // duplicate/laaReference checks and content schema validation) returns the new
    // application. The controller only reads .id() to build the Location header.
    when(createApplicationUseCase.execute(any()))
        .thenReturn(ApplicationDomain.builder().id(CREATED_APPLICATION_ID).build());
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Placeholders — implement when consumer publishes pacts that need these.
  //
  // Each @State string must literally match what the consumer writes in
  // .given("..."). Don't invent states here; agree them with the consumer
  // team and add the matching handler when the pact arrives.
  // ═══════════════════════════════════════════════════════════════════════════

  // @State("no applications exist")
  // public void noApplicationsExist() {
  //   log.info("Setting up state: no applications exist");
  //   when(getAllApplicationsUseCase.execute(any()))
  //       .thenReturn(new GetAllApplicationsResult(new PagedResult<>(List.of(), 0L), 1, 10));
  // }
}

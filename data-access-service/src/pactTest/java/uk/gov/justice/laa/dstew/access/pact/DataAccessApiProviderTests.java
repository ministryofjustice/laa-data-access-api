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
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummary;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.utils.PaginationHelper.PaginatedResult;

/**
 * Pact provider verification entry point for {@code laa-data-access-api}.
 *
 * <p>Pulls every consumer pact registered against the provider name below from the broker, runs
 * each interaction against this Spring Boot app on a random local port, and (when broker creds
 * are set in env) publishes the verification result back.
 *
 * <p>Each {@code @State("...")} method on this class is the provider-side implementation of a
 * state string a consumer named via {@code .given("...")} when they generated their pact.
 *
 * <p>State strings live across repos: the literal string here must match the literal string
 * the consumer wrote. Agree these with the consumer team before changing.
 */
@Slf4j
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Provider("laa-data-access-api")
@PactBroker
public class DataAccessApiProviderTests extends AbstractProviderPactTests {

  @LocalServerPort private int port;

  @BeforeEach
  void setUp(PactVerificationContext context) {
    context.setTarget(new HttpTestTarget("localhost", port));
  }

  @TestTemplate
  @ExtendWith(PactVerificationInvocationContextProvider.class)
  void pactVerificationTestTemplate(PactVerificationContext context) {
    context.verifyInteraction();
  }

  /**
   * Injects the headers the BFF consumer sends on every request. The consumer's pact asserts these
   * are present, so verification needs them on the replayed request even though we run with
   * {@code feature.disable-security=true}.
   */
  @TargetRequestFilter
  public void requestFilter(HttpRequest request) {
    request.setHeader("Authorization", "Bearer swagger-caseworker-token");
    request.setHeader("X-Service-Name", "CIVIL_DECIDE");
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // GET /api/v0/applications — happy path
  // Consumer: laa-civil-decide-api
  // ═══════════════════════════════════════════════════════════════════════════

  @State("applications exist")
  public void applicationsExist() {
    log.info("Setting up state: applications exist");

    ApplicationSummary sample =
        new ApplicationSummary()
            .applicationId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            .laaReference("LAA-REF-0001")
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .applicationType(ApplicationType.INITIAL)
            .categoryOfLaw(CategoryOfLaw.FAMILY)
            .matterType(MatterType.SPECIAL_CHILDREN_ACT)
            .isLead(true)
            .autoGrant(false)
            .usedDelegatedFunctions(false)
            .submittedAt(OffsetDateTime.parse("2026-01-15T10:00:00Z"))
            .lastUpdated(OffsetDateTime.parse("2026-01-16T12:30:00Z"))
            .clientFirstName("Test")
            .clientLastName("Client")
            .clientDateOfBirth(LocalDate.parse("1980-01-01"))
            .officeCode("0A001D");

    Page<ApplicationSummary> page = new PageImpl<>(List.of(sample), PageRequest.of(0, 10), 1L);
    PaginatedResult<ApplicationSummary> result = new PaginatedResult<>(page, 1, 10);

    when(applicationSummaryService.getAllApplications(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(result);
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
  //   Page<ApplicationSummary> empty = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);
  //   when(applicationSummaryService.getAllApplications(
  //           any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
  //       .thenReturn(new PaginatedResult<>(empty, 1, 10));
  // }

  // @State("individuals exist for an application")
  // public void individualsExistForAnApplication() {
  //   log.info("Setting up state: individuals exist for an application");
  //   // when(individualsService.getIndividuals(any(), any(), any())).thenReturn(...);
  // }

  // @State("no individuals exist for an application")
  // public void noIndividualsExistForAnApplication() {
  //   log.info("Setting up state: no individuals exist for an application");
  //   // when(individualsService.getIndividuals(any(), any(), any())).thenReturn(empty);
  // }

  // @State("an application exists by id")
  // public void anApplicationExistsById() {
  //   log.info("Setting up state: an application exists by id");
  //   // when(applicationService.getApplication(any())).thenReturn(...);
  // }
}

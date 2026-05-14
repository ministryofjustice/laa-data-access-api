package uk.gov.justice.laa.dstew.access.pact;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.ApplicationSummaryRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.repository.CertificateRepository;
import uk.gov.justice.laa.dstew.access.repository.DecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.repository.IndividualRepository;
import uk.gov.justice.laa.dstew.access.repository.MeritsDecisionRepository;
import uk.gov.justice.laa.dstew.access.repository.NoteRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.service.ApplicationService;
import uk.gov.justice.laa.dstew.access.service.ApplicationSummaryService;
import uk.gov.justice.laa.dstew.access.service.CaseworkerService;
import uk.gov.justice.laa.dstew.access.service.CertificateService;
import uk.gov.justice.laa.dstew.access.service.DomainEventService;
import uk.gov.justice.laa.dstew.access.service.IndividualsService;
import uk.gov.justice.laa.dstew.access.service.ProceedingsService;

/**
 * Shared scaffolding for Pact provider tests. Brings up the Spring Web layer with all
 * persistence-layer beans mocked, so verification runs without a real database or Flyway.
 *
 * <p>Pattern mirrors `laa-data-claims-api` (AbstractProviderPactTests in that repo). State setup
 * lives on the concrete subclass.
 */
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
public class AbstractProviderPactTests {

  // ─── Service-layer mocks (state handlers stub these) ─────────────────────────
  @MockitoBean protected ApplicationSummaryService applicationSummaryService;
  @MockitoBean protected ApplicationService applicationService;
  @MockitoBean protected IndividualsService individualsService;
  @MockitoBean protected CertificateService certificateService;
  @MockitoBean protected DomainEventService domainEventService;
  @MockitoBean protected CaseworkerService caseworkerService;
  @MockitoBean protected ProceedingsService proceedingsService;

  // ─── Repository mocks ────────────────────────────────────────────────────────
  @MockitoBean protected ApplicationRepository applicationRepository;
  @MockitoBean protected ApplicationSummaryRepository applicationSummaryRepository;
  @MockitoBean protected CaseworkerRepository caseworkerRepository;
  @MockitoBean protected CertificateRepository certificateRepository;
  @MockitoBean protected DecisionRepository decisionRepository;
  @MockitoBean protected DomainEventRepository domainEventRepository;
  @MockitoBean protected IndividualRepository individualRepository;
  @MockitoBean protected MeritsDecisionRepository meritsDecisionRepository;
  @MockitoBean protected NoteRepository noteRepository;
  @MockitoBean protected ProceedingRepository proceedingRepository;

  // ─── Infra mocks so Spring Boot starts without a real DB ─────────────────────
  @MockitoBean protected Flyway flyway;
  @MockitoBean protected EntityManagerFactory entityManagerFactory;
  @MockitoBean protected DataSource dataSource;
  @MockitoBean protected PathMappedEndpoints pathMappedEndpoints;
}

package uk.gov.justice.laa.dstew.access.pact;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.dstew.access.repository.ApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.repository.CertificateRepository;
import uk.gov.justice.laa.dstew.access.repository.DomainEventRepository;
import uk.gov.justice.laa.dstew.access.repository.IndividualRepository;
import uk.gov.justice.laa.dstew.access.repository.LinkedApplicationRepository;
import uk.gov.justice.laa.dstew.access.repository.NoteRepository;
import uk.gov.justice.laa.dstew.access.repository.ProceedingRepository;
import uk.gov.justice.laa.dstew.access.service.applications.SdsService;
import uk.gov.justice.laa.dstew.access.service.applications.UnassignCaseworkerService;
import uk.gov.justice.laa.dstew.access.service.caseworkers.GetAllCaseworkersService;
import uk.gov.justice.laa.dstew.access.service.domainevents.GetDomainEventService;
import uk.gov.justice.laa.dstew.access.service.domainevents.SaveDomainEventService;
import uk.gov.justice.laa.dstew.access.service.sds.TokenService;
import uk.gov.justice.laa.dstew.access.usecase.assigncaseworker.AssignCaseworkerUseCase;
import uk.gov.justice.laa.dstew.access.usecase.createapplication.CreateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.usecase.createnote.CreateNoteUseCase;
import uk.gov.justice.laa.dstew.access.usecase.getallapplications.GetAllApplicationsUseCase;
import uk.gov.justice.laa.dstew.access.usecase.getallindividuals.GetAllIndividualsUseCase;
import uk.gov.justice.laa.dstew.access.usecase.getallnotesforapplication.GetAllNotesForApplicationUseCase;
import uk.gov.justice.laa.dstew.access.usecase.getapplication.GetApplicationUseCase;
import uk.gov.justice.laa.dstew.access.usecase.getcertificate.GetCertificateUseCase;
import uk.gov.justice.laa.dstew.access.usecase.makedecision.MakeDecisionUseCase;
import uk.gov.justice.laa.dstew.access.usecase.updateapplication.UpdateApplicationUseCase;

/**
 * Shared scaffolding for Pact provider tests. Brings up the Spring Web layer with all
 * persistence-layer beans mocked, so verification runs without a real database or Flyway.
 *
 * <p>Pattern mirrors `laa-data-claims-api` (AbstractProviderPactTests in that repo). State setup
 * lives on the concrete subclass.
 */
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
public class AbstractProviderPactTests {

  // ─── Use-case mocks (state handlers stub these) ──────────────────────────────
  @MockitoBean protected GetAllApplicationsUseCase getAllApplicationsUseCase;
  @MockitoBean protected GetApplicationUseCase getApplicationUseCase;
  @MockitoBean protected GetAllIndividualsUseCase getAllIndividualsUseCase;
  @MockitoBean protected GetCertificateUseCase getCertificateUseCase;
  @MockitoBean protected GetAllNotesForApplicationUseCase getAllNotesForApplicationUseCase;
  @MockitoBean protected CreateApplicationUseCase createApplicationUseCase;
  @MockitoBean protected UpdateApplicationUseCase updateApplicationUseCase;
  @MockitoBean protected AssignCaseworkerUseCase assignCaseworkerUseCase;
  @MockitoBean protected MakeDecisionUseCase makeDecisionUseCase;
  @MockitoBean protected CreateNoteUseCase createNoteUseCase;

  // ─── Service-layer mocks ─────────────────────────────────────────────────────
  @MockitoBean protected UnassignCaseworkerService unassignCaseworkerService;
  @MockitoBean protected GetAllCaseworkersService getAllCaseworkersService;
  @MockitoBean protected GetDomainEventService getDomainEventService;
  @MockitoBean protected SaveDomainEventService saveDomainEventService;
  @MockitoBean protected SdsService sdsService;
  @MockitoBean protected TokenService tokenService;

  // ─── Repository mocks ────────────────────────────────────────────────────────
  @MockitoBean protected ApplicationRepository applicationRepository;
  @MockitoBean protected CaseworkerRepository caseworkerRepository;
  @MockitoBean protected CertificateRepository certificateRepository;
  @MockitoBean protected DomainEventRepository domainEventRepository;
  @MockitoBean protected IndividualRepository individualRepository;
  @MockitoBean protected LinkedApplicationRepository linkedApplicationRepository;
  @MockitoBean protected NoteRepository noteRepository;
  @MockitoBean protected ProceedingRepository proceedingRepository;

  // ─── Infra mocks so Spring Boot starts without a real DB ─────────────────────
  @MockitoBean protected Flyway flyway;
  @MockitoBean protected EntityManagerFactory entityManagerFactory;
  @MockitoBean protected DataSource dataSource;
  @MockitoBean protected PathMappedEndpoints pathMappedEndpoints;
}

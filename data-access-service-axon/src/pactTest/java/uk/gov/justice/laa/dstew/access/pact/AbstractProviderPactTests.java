package uk.gov.justice.laa.dstew.access.pact;

import java.nio.charset.StandardCharsets;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.dstew.access.DataAccessServiceAxonApplication;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionUseCase;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkApplicationUseCase;
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteUseCase;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityDraftUseCase;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.SubmitPriorAuthorityDraftUseCase;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionUseCase;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.UploadPriorAuthorityDocumentUseCase;
import uk.gov.justice.laa.dstew.access.command.application.ready.RecordAutoGrantOutcomeUseCase;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignWorkItemUseCase;
import uk.gov.justice.laa.dstew.access.controller.application.AutoGrantOutcomeCommandMapper;
import uk.gov.justice.laa.dstew.access.controller.application.CreateApplicationCommandMapper;
import uk.gov.justice.laa.dstew.access.controller.application.CreateNoteCommandMapper;
import uk.gov.justice.laa.dstew.access.controller.application.MakeDecisionCommandMapper;
import uk.gov.justice.laa.dstew.access.controller.application.SavePriorAuthorityDraftCommandMapper;
import uk.gov.justice.laa.dstew.access.query.worklist.WorkListItemReadRepository;
import uk.gov.justice.laa.dstew.access.service.sds.SdsDownloadResult;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
import uk.gov.justice.laa.dstew.access.service.sds.SdsUploadResult;

/**
 * Shared scaffolding for Pact provider verification.
 *
 * <p>Boots the real Axon application on a random port against in-memory H2 (see {@code
 * src/pactTest/resources/application-pact.yml}). Nothing below the controllers is mocked. Provider
 * states create data by dispatching real commands through the use cases, then wait until the
 * relevant projection can serve it, so the replayed request reads from a real read model.
 *
 * <p>Only systems outside the service are faked. Secure Document Storage is replaced by a Mockito
 * bean, the same way the module's integration tests do it, until a consumer contract with the SDS
 * team exists. Login uses the dev token and SNS publication is switched off.
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

  @Autowired private QueryGateway queryGateway;
  @Autowired private AxonConfiguration axonConfiguration;
  @Autowired private WorkListItemReadRepository workListItemReadRepository;
  @Autowired private CreateApplicationUseCase createApplicationUseCase;
  @Autowired private RecordAutoGrantOutcomeUseCase recordAutoGrantOutcomeUseCase;
  @Autowired private AssignWorkItemUseCase assignWorkItemUseCase;
  @Autowired private MakeApplicationDecisionUseCase makeApplicationDecisionUseCase;
  @Autowired private CreateNoteUseCase createNoteUseCase;
  @Autowired private LinkApplicationUseCase linkApplicationUseCase;
  @Autowired private CreatePriorAuthorityDraftUseCase createPriorAuthorityDraftUseCase;
  @Autowired private SubmitPriorAuthorityDraftUseCase submitPriorAuthorityDraftUseCase;
  @Autowired private MakePriorAuthorityDecisionUseCase makePriorAuthorityDecisionUseCase;
  @Autowired private UploadPriorAuthorityDocumentUseCase uploadPriorAuthorityDocumentUseCase;
  @Autowired private CreateApplicationCommandMapper createApplicationCommandMapper;
  @Autowired private AutoGrantOutcomeCommandMapper autoGrantOutcomeCommandMapper;
  @Autowired private MakeDecisionCommandMapper makeDecisionCommandMapper;
  @Autowired private CreateNoteCommandMapper createNoteCommandMapper;
  @Autowired private SavePriorAuthorityDraftCommandMapper savePriorAuthorityDraftCommandMapper;

  /**
   * Secure Document Storage is an external system. Replaced at the client boundary, as the
   * integration tests do, and stubbed before every interaction so uploads and downloads succeed.
   */
  @MockitoBean protected SdsService sdsService;

  protected ProviderStates states;

  /**
   * State handlers call secured use cases directly, outside any HTTP request, so they need an
   * authenticated caseworker on the test thread. The replayed HTTP request authenticates separately
   * through the real security filter chain.
   */
  @BeforeEach
  void prepareStateSetup() {
    TestingAuthenticationToken authentication =
        new TestingAuthenticationToken(
            STATE_SETUP_PRINCIPAL,
            "n/a",
            new SimpleGrantedAuthority("APPROLE_LAA_CASEWORKER"),
            new SimpleGrantedAuthority("ROLE_LAA_CASEWORKER"));
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    stubSecureDocumentStorage();

    ApplicationStateSeeder applications =
        new ApplicationStateSeeder(
            queryGateway,
            workListItemReadRepository,
            createApplicationUseCase,
            recordAutoGrantOutcomeUseCase,
            assignWorkItemUseCase,
            makeApplicationDecisionUseCase,
            createNoteUseCase,
            linkApplicationUseCase,
            createApplicationCommandMapper,
            autoGrantOutcomeCommandMapper,
            makeDecisionCommandMapper,
            createNoteCommandMapper);
    PriorAuthorityStateSeeder priorAuthorities =
        new PriorAuthorityStateSeeder(
            queryGateway,
            applications,
            createPriorAuthorityDraftUseCase,
            submitPriorAuthorityDraftUseCase,
            makePriorAuthorityDecisionUseCase,
            uploadPriorAuthorityDocumentUseCase,
            assignWorkItemUseCase,
            savePriorAuthorityDraftCommandMapper);
    states = new ProviderStates(applications, priorAuthorities, axonConfiguration);
  }

  @AfterEach
  void clearStateSetupAuthentication() {
    SecurityContextHolder.clearContext();
  }

  private void stubSecureDocumentStorage() {
    SdsUploadResult uploaded =
        new SdsUploadResult(
            "pact-bucket/evidence.pdf", "File uploaded successfully", "pact-checksum-0001");
    Mockito.when(
            sdsService.saveEvidenceFile(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(uploaded);
    Mockito.when(sdsService.saveFile(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(uploaded);
    Mockito.when(
            sdsService.getEvidenceFile(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(
            new ByteArrayResource(
                "%PDF-1.4\nPact provider state evidence".getBytes(StandardCharsets.US_ASCII)));
    Mockito.when(sdsService.getFile(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(new SdsDownloadResult("https://sds.example.test/files/evidence.pdf"));
  }
}

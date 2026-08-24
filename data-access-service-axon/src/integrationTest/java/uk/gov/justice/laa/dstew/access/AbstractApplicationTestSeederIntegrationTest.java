package uk.gov.justice.laa.dstew.access;

import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationUseCase;
import uk.gov.justice.laa.dstew.access.command.application.assignment.AssignCaseworkerUseCase;
import uk.gov.justice.laa.dstew.access.command.application.assignment.UnassignCaseworkerUseCase;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionUseCase;
import uk.gov.justice.laa.dstew.access.command.application.ready.RecordAutoGrantOutcomeUseCase;
import uk.gov.justice.laa.dstew.access.controller.application.AssignCaseworkerRequestMapper;
import uk.gov.justice.laa.dstew.access.controller.application.AutoGrantOutcomeCommandMapper;
import uk.gov.justice.laa.dstew.access.controller.application.CreateApplicationCommandMapper;
import uk.gov.justice.laa.dstew.access.controller.application.MakeDecisionCommandMapper;
import uk.gov.justice.laa.dstew.access.controller.application.UnassignCaseworkerRequestMapper;
import uk.gov.justice.laa.dstew.access.testutils.ApplicationTestDataSeeder;

abstract class AbstractApplicationTestSeederIntegrationTest {

  private static final String TEST_USER = "integration-test-caseworker";

  @Autowired protected QueryGateway queryGateway;
  @Autowired protected CreateApplicationUseCase createApplicationUseCase;
  @Autowired protected MakeApplicationDecisionUseCase makeApplicationDecisionUseCase;
  @Autowired protected RecordAutoGrantOutcomeUseCase recordAutoGrantOutcomeUseCase;
  @Autowired protected AssignCaseworkerUseCase assignCaseworkerUseCase;
  @Autowired protected UnassignCaseworkerUseCase unassignCaseworkerUseCase;
  @Autowired protected CreateApplicationCommandMapper createApplicationCommandMapper;
  @Autowired protected MakeDecisionCommandMapper makeDecisionCommandMapper;
  @Autowired protected AutoGrantOutcomeCommandMapper autoGrantOutcomeCommandMapper;
  @Autowired protected AssignCaseworkerRequestMapper assignCaseworkerRequestMapper;
  @Autowired protected UnassignCaseworkerRequestMapper unassignCaseworkerRequestMapper;

  @BeforeEach
  void setSecurityContext() {
    var authentication =
        new TestingAuthenticationToken(
            TEST_USER,
            "password",
            new SimpleGrantedAuthority("APPROLE_LAA_CASEWORKER"),
            new SimpleGrantedAuthority("ROLE_LAA_CASEWORKER"));
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  protected ApplicationTestDataSeeder newSeeder() {
    return new ApplicationTestDataSeeder(
        queryGateway,
        createApplicationUseCase,
        makeApplicationDecisionUseCase,
        recordAutoGrantOutcomeUseCase,
        assignCaseworkerUseCase,
        unassignCaseworkerUseCase,
        createApplicationCommandMapper,
        makeDecisionCommandMapper,
        autoGrantOutcomeCommandMapper,
        assignCaseworkerRequestMapper,
        unassignCaseworkerRequestMapper);
  }
}

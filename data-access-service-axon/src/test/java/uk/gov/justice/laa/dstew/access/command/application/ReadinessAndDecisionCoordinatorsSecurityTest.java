package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCoordinator;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCommand;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCoordinator;
import uk.gov.justice.laa.dstew.access.command.application.ready.ReadyApplicationResult;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.utils.BaseSecuredUseCaseTest;
import uk.gov.justice.laa.dstew.access.utils.TestSecurityConfig;

@SpringBootTest(
    classes = {
      MarkApplicationReadyCoordinator.class,
      MakeApplicationDecisionCoordinator.class,
      TestSecurityConfig.class
    })
@ImportAutoConfiguration(
    exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
class ReadinessAndDecisionCoordinatorsSecurityTest extends BaseSecuredUseCaseTest {
  private static final String AUTO_GRANT_ROLE = "ROLE_LAA_DECIDE_AUTO_GRANT";
  @Autowired private MarkApplicationReadyCoordinator readyCoordinator;
  @Autowired private MakeApplicationDecisionCoordinator decisionCoordinator;

  @MockitoBean private RetryingCommandDispatcher dispatcher;
  @MockitoBean private ApplicationGroupRouteResolver applicationGroupRouteResolver;
  @MockitoBean private WorkItemRouteResolver workItemRouteResolver;

  @Test
  void givenNoRole_whenCoordinatorsCalled_thenAccessDenied() {
    setSecurityContext(NO_ROLE);

    assertDenied();

    verifyNoInteractions(dispatcher, applicationGroupRouteResolver, workItemRouteResolver);
  }

  @Test
  void givenBlankName_whenCoordinatorsCalled_thenAccessDenied() {
    setSecurityContextWithName(" ", CASEWORKER_ROLE);

    assertDenied();

    verifyNoInteractions(dispatcher, applicationGroupRouteResolver, workItemRouteResolver);
  }

  @Test
  void givenAutoGrantServiceRole_whenReadinessRecorded_thenAllowed() {
    setSecurityContext(AUTO_GRANT_ROLE);
    MarkApplicationReadyCommand command =
        new MarkApplicationReadyCommand(UUID.randomUUID(), "{}", Instant.now());

    assertThatCode(() -> readyCoordinator.handle(command)).doesNotThrowAnyException();

    verify(dispatcher).dispatch(command, ReadyApplicationResult.class);
  }

  private void assertDenied() {
    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(
            () ->
                readyCoordinator.handle(
                    new MarkApplicationReadyCommand(UUID.randomUUID(), "{}", Instant.now())))
        .withMessageContaining("Access Denied");
    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(
            () ->
                decisionCoordinator.handle(
                    new MakeApplicationDecisionCommand(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1L,
                        "REFUSED",
                        List.of(),
                        null,
                        "{}",
                        "decision",
                        Instant.now(),
                        null)))
        .withMessageContaining("Access Denied");
  }
}

package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
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
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;
import uk.gov.justice.laa.dstew.access.utils.BaseSecuredUseCaseTest;
import uk.gov.justice.laa.dstew.access.utils.TestSecurityConfig;

@SpringBootTest(classes = {UnlinkApplicationCommandHandler.class, TestSecurityConfig.class})
@ImportAutoConfiguration(
    exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
class UnlinkApplicationCommandHandlerSecurityTest extends BaseSecuredUseCaseTest {

  @Autowired private UnlinkApplicationCommandHandler handler;

  @MockitoBean private ApplicationGroupRouteResolver routeResolver;
  @MockitoBean private RetryingCommandDispatcher dispatcher;

  @Test
  void givenNoRole_whenHandled_thenThrowsAuthorizationDeniedException() {
    setSecurityContext(NO_ROLE);

    assertDenied();

    verifyNoInteractions(routeResolver, dispatcher);
  }

  @Test
  void givenBlankAuthenticatedName_whenHandled_thenThrowsAuthorizationDeniedException() {
    setSecurityContextWithName(" ", CASEWORKER_ROLE);

    assertDenied();

    verifyNoInteractions(routeResolver, dispatcher);
  }

  private void assertDenied() {
    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> handler.handle(command()))
        .withMessageContaining("Access Denied");
  }

  private UnlinkApplicationCommand command() {
    return new UnlinkApplicationCommand(
        UUID.randomUUID(), 0, Instant.parse("2026-09-14T15:00:00Z"));
  }
}

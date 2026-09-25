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

@SpringBootTest(classes = {LinkApplicationCommandHandler.class, TestSecurityConfig.class})
@ImportAutoConfiguration(
    exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
class LinkApplicationCommandHandlerSecurityTest extends BaseSecuredUseCaseTest {

  @Autowired private LinkApplicationCommandHandler handler;

  @MockitoBean private ApplicationGroupRouteResolver routeResolver;
  @MockitoBean private RetryingCommandDispatcher dispatcher;

  @Test
  void givenNoRole_whenHandle_thenThrowsAuthorizationDeniedException() {
    setSecurityContext(NO_ROLE);

    assertDenied();

    verifyNoInteractions(routeResolver, dispatcher);
  }

  @Test
  void givenBlankAuthenticatedName_whenHandle_thenThrowsAuthorizationDeniedException() {
    setSecurityContextWithName(" ", CASEWORKER_ROLE);

    assertDenied();

    verifyNoInteractions(routeResolver, dispatcher);
  }

  private void assertDenied() {
    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> handler.handle(linkCommand()))
        .withMessageContaining("Access Denied");
  }

  private LinkApplicationCommand linkCommand() {
    return new LinkApplicationCommand(
        UUID.randomUUID(), UUID.randomUUID(), LinkType.FAMILY, Instant.now());
  }
}

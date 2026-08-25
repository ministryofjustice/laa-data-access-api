package uk.gov.justice.laa.dstew.access.command.application.assignment;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.justice.laa.dstew.access.command.caseworker.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.utils.BaseSecuredUseCaseTest;
import uk.gov.justice.laa.dstew.access.utils.TestSecurityConfig;

@SpringBootTest(classes = {AssignCaseworkerUseCase.class, TestSecurityConfig.class})
@ImportAutoConfiguration(
    exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
class AssignCaseworkerServiceSecurityTest extends BaseSecuredUseCaseTest {

  @Autowired private AssignCaseworkerUseCase useCase;

  @MockitoBean private CaseworkerRepository caseworkerRepository;

  @MockitoBean private CommandGateway commandGateway;

  @Test
  void givenNoRole_whenAssign_thenThrowsAuthorizationDeniedException() {
    setSecurityContext(NO_ROLE);

    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> useCase.assign(UUID.randomUUID(), UUID.randomUUID(), "{}", null))
        .withMessageContaining("Access Denied");

    verifyNoInteractions(caseworkerRepository, commandGateway);
  }

  @Test
  void givenBlankAuthenticatedName_whenAssign_thenThrowsAuthorizationDeniedException() {
    setSecurityContextWithName(" ", CASEWORKER_ROLE);

    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> useCase.assign(UUID.randomUUID(), UUID.randomUUID(), "{}", null))
        .withMessageContaining("Access Denied");

    verifyNoInteractions(caseworkerRepository, commandGateway);
  }
}

package uk.gov.justice.laa.dstew.access.service.caseworkers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import uk.gov.justice.laa.dstew.access.command.caseworker.Caseworker;
import uk.gov.justice.laa.dstew.access.command.caseworker.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.utils.BaseSecuredUseCaseTest;
import uk.gov.justice.laa.dstew.access.utils.TestSecurityConfig;

@SpringBootTest(classes = {GetCaseworkersService.class, TestSecurityConfig.class})
@ImportAutoConfiguration(
    exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
class GetCaseworkersServiceSecurityTest extends BaseSecuredUseCaseTest {

  @Autowired private GetCaseworkersService service;

  @MockitoBean private CaseworkerRepository caseworkerRepository;

  @Test
  void givenNoRole_whenGetCaseworkers_thenThrowsAuthorizationDeniedException() {
    setSecurityContext(NO_ROLE);

    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> service.getCaseworkers())
        .withMessageContaining("Access Denied");

    verifyNoInteractions(caseworkerRepository);
  }

  @Test
  void givenBlankAuthenticatedName_whenGetCaseworkers_thenThrowsAuthorizationDeniedException() {
    setSecurityContextWithName(" ", CASEWORKER_ROLE);

    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(() -> service.getCaseworkers())
        .withMessageContaining("Access Denied");

    verifyNoInteractions(caseworkerRepository);
  }

  @Test
  void givenCaseworkerRoleAndName_whenGetCaseworkers_thenReturnsMappedCaseworkers() {
    setSecurityContext(CASEWORKER_ROLE);
    UUID id = UUID.randomUUID();
    when(caseworkerRepository.findAll())
        .thenReturn(List.of(new Caseworker(id, "alice@example.com")));

    assertThat(service.getCaseworkers())
        .singleElement()
        .satisfies(
            caseworker -> {
              assertThat(caseworker.getId()).isEqualTo(id);
              assertThat(caseworker.getUsername()).isEqualTo("alice@example.com");
            });
  }
}

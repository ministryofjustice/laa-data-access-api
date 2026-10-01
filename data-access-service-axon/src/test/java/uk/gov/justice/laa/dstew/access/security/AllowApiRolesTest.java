package uk.gov.justice.laa.dstew.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.utils.BaseSecuredUseCaseTest;
import uk.gov.justice.laa.dstew.access.utils.TestSecurityConfig;

@SpringBootTest(
    classes = {
      AllowApiRolesTest.MultiRoleComponent.class,
      AllowApiRolesTest.SingleRoleComponent.class,
      TestSecurityConfig.class
    })
class AllowApiRolesTest extends BaseSecuredUseCaseTest {

  private static final String OTHER_ROLE = "ROLE_SOME_OTHER_ROLE";

  @Autowired private MultiRoleComponent component;
  @Autowired private SingleRoleComponent singleRoleComponent;

  @Test
  void givenNoMatchingRole_whenCalled_thenThrowsAuthorizationDeniedException() {
    setSecurityContext(NO_ROLE);

    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(component::call)
        .withMessageContaining("Access Denied");
  }

  @Test
  void givenBlankAuthenticatedName_whenCalled_thenThrowsAuthorizationDeniedException() {
    setSecurityContextWithName(" ", CASEWORKER_ROLE);

    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(component::call)
        .withMessageContaining("Access Denied");
  }

  @ParameterizedTest
  @ValueSource(strings = {CASEWORKER_ROLE, OTHER_ROLE})
  void givenMatchingRole_whenCalled_thenReturnsResult(String role) {
    setSecurityContext(role);

    assertThat(component.call()).isEqualTo("ok");
  }

  @ParameterizedTest
  @ValueSource(strings = {CASEWORKER_ROLE, OTHER_ROLE})
  void givenEmptyAllowedRoles_whenCalled_thenThrowsAuthorizationDeniedException(String role) {
    setSecurityContext(role);

    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(component::callWithNoAllowedRoles);
  }

  @Test
  void givenMatchingClassLevelRole_whenCalled_thenReturnsResult() {
    setSecurityContext(CASEWORKER_ROLE);

    assertThat(singleRoleComponent.call()).isEqualTo("ok");
  }

  @Test
  void givenNonMatchingClassLevelRole_whenCalled_thenThrowsAuthorizationDeniedException() {
    setSecurityContext(OTHER_ROLE);

    assertThatExceptionOfType(AuthorizationDeniedException.class)
        .isThrownBy(singleRoleComponent::call);
  }

  @Component
  static class MultiRoleComponent {

    @AllowApiRoles({"LAA_CASEWORKER", "SOME_OTHER_ROLE"})
    String call() {
      return "ok";
    }

    @AllowApiRoles({})
    String callWithNoAllowedRoles() {
      return "ok";
    }
  }

  @Component
  @AllowApiRoles("LAA_CASEWORKER")
  static class SingleRoleComponent {

    String call() {
      return "ok";
    }
  }
}

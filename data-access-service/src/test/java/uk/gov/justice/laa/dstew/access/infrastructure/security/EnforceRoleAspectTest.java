package uk.gov.justice.laa.dstew.access.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.AccessPolicy;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.EnforceRole;
import uk.gov.justice.laa.dstew.access.usecase.shared.security.RequiredRole;

@ExtendWith(SpringExtension.class)
@EnableAspectJAutoProxy
@Import({EnforceRoleAspect.class, EnforceRoleAspectTest.StubUseCase.class})
class EnforceRoleAspectTest {

  @MockitoBean AccessPolicy accessPolicy;
  @Autowired StubUseCase stubUseCase;

  @Test
  void passesFullAnnotationToAccessPolicy() {
    stubUseCase.doSomething();
    ArgumentCaptor<EnforceRole> captor = ArgumentCaptor.forClass(EnforceRole.class);
    verify(accessPolicy).enforce(captor.capture());
    assertThat(captor.getValue().anyOf()).containsExactly(RequiredRole.API_CASEWORKER);
  }

  @Test
  void throwsWhenAccessPolicyDenies() {
    doThrow(new AccessDeniedException("denied")).when(accessPolicy).enforce(any(EnforceRole.class));
    assertThatThrownBy(() -> stubUseCase.doSomething()).isInstanceOf(AccessDeniedException.class);
  }

  @Component
  static class StubUseCase {
    @EnforceRole(anyOf = RequiredRole.API_CASEWORKER)
    public void doSomething() {}
  }
}

package uk.gov.justice.laa.dstew.access.utils;

import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Shared helpers for method-security tests. */
public abstract class BaseSecuredUseCaseTest {

  protected static final String CASEWORKER_ROLE = "ROLE_LAA_CASEWORKER";
  protected static final String NO_ROLE = "ROLE_NoRole_DO_NOT_USE";

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  protected void setSecurityContext(String... roles) {
    setSecurityContextWithName("user", roles);
  }

  protected void setSecurityContextWithName(String name, String... roles) {
    var authorities = Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
    var authentication = new TestingAuthenticationToken(name, "password", authorities);
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}

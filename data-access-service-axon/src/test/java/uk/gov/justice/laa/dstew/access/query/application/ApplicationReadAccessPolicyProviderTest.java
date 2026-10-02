package uk.gov.justice.laa.dstew.access.query.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import uk.gov.justice.laa.dstew.access.config.ServiceNameContext;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.query.utils.security.OfficeCodeReadAccessScope;
import uk.gov.justice.laa.dstew.access.query.utils.security.UnrestrictedReadAccessScope;

class ApplicationReadAccessPolicyProviderTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void givenNonCivilManageService_whenResolved_thenAccessIsUnrestricted() {
    ServiceNameContext context = context(ServiceName.CIVIL_APPLY);

    assertThat(new ApplicationReadAccessPolicyProvider(context).resolve())
        .isInstanceOf(UnrestrictedReadAccessScope.class);
  }

  @Test
  void givenCivilManageAccounts_whenResolved_thenReturnsDistinctTrimmedOfficeCodes() {
    ServiceNameContext context = context(ServiceName.CIVIL_MANAGE);
    authenticate(List.of(" A ", "B", "A", " "));

    OfficeCodeReadAccessScope scope =
        (OfficeCodeReadAccessScope) new ApplicationReadAccessPolicyProvider(context).resolve();

    assertThat(scope.permittedOfficeCodes()).containsExactlyInAnyOrder("A", "B");
  }

  @Test
  void givenCivilManageWithoutAccounts_whenResolved_thenReturnsEmptyOfficeScope() {
    ServiceNameContext context = context(ServiceName.CIVIL_MANAGE);

    OfficeCodeReadAccessScope scope =
        (OfficeCodeReadAccessScope) new ApplicationReadAccessPolicyProvider(context).resolve();

    assertThat(scope.permittedOfficeCodes()).isEmpty();
  }

  private static ServiceNameContext context(ServiceName serviceName) {
    ServiceNameContext context = new ServiceNameContext();
    context.setServiceName(serviceName);
    return context;
  }

  private static void authenticate(List<String> accounts) {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("LAA_ACCOUNTS", accounts));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CASEWORKER"))));
  }
}


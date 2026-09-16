package uk.gov.justice.laa.dstew.access.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SecondaryAuthorizationFilterTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void givenSecondaryAuthorizationEntitlements_whenFiltered_thenMakesClaimsAndRolesAvailable()
      throws Exception {
    Jwt oboJwt = jwt("obo-token").claim("obo-only", "preserved").build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(
                oboJwt, List.of(new SimpleGrantedAuthority("ROLE_LAA_CASEWORKER"))));
    Jwt authorizationJwt =
        jwt("x-authorization-token")
            .claim("LAA_APP_ROLES", List.of("CASEWORKER", "CASEWORKER", ""))
            .claim("LAA_ACCOUNTS", List.of("ABC123", "DEF456", "ABC123"))
            .build();
    SecondaryAuthorizationFilter filter =
        new SecondaryAuthorizationFilter(token -> authorizationJwt);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Authorization", "x-authorization-token");
    AtomicBoolean chainInvoked = new AtomicBoolean();

    filter.doFilterInternal(
        request,
        new MockHttpServletResponse(),
        (servletRequest, servletResponse) -> chainInvoked.set(true));

    assertThat(chainInvoked).isTrue();
    assertThat(SecurityContextHolder.getContext().getAuthentication())
        .isInstanceOf(JwtAuthenticationToken.class);
    JwtAuthenticationToken authentication =
        (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getToken().getClaimAsStringList("LAA_APP_ROLES"))
        .containsExactly("CASEWORKER");
    assertThat(authentication.getToken().getClaimAsStringList("LAA_ACCOUNTS"))
        .containsExactly("ABC123", "DEF456");
    assertThat(authentication.getToken().getClaimAsString("obo-only")).isEqualTo("preserved");
    assertThat(authentication.getAuthorities())
        .extracting(authority -> authority.getAuthority())
        .contains("ROLE_LAA_CASEWORKER", "APPROLE_CASEWORKER");
  }

  @Test
  void givenOnlyAccountsClaim_whenFiltered_thenMakesAccountsAvailable() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt("obo-token").build()));
    Jwt authorizationJwt =
        jwt("x-authorization-token").claim("LAA_ACCOUNTS", List.of("ABC123")).build();
    SecondaryAuthorizationFilter filter =
        new SecondaryAuthorizationFilter(token -> authorizationJwt);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Authorization", "x-authorization-token");

    filter.doFilterInternal(
        request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> {});

    JwtAuthenticationToken authentication =
        (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getToken().getClaimAsStringList("LAA_ACCOUNTS"))
        .containsExactly("ABC123");
  }

  @Test
  void givenSecondaryAuthorizationTokenWithDifferentOid_whenFiltered_thenReturnsUnauthorized()
      throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt("obo-token").build()));
    Jwt authorizationJwt =
        jwt("x-authorization-token")
            .claim("oid", "different-entra-object-id")
            .claim("LAA_ACCOUNTS", List.of("ABC123"))
            .build();
    SecondaryAuthorizationFilter filter =
        new SecondaryAuthorizationFilter(token -> authorizationJwt);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Authorization", "x-authorization-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean chainInvoked = new AtomicBoolean();

    filter.doFilterInternal(
        request, response, (servletRequest, servletResponse) -> chainInvoked.set(true));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chainInvoked).isFalse();
  }

  @Test
  void givenInvalidSecondaryAuthorizationToken_whenFiltered_thenReturnsUnauthorized()
      throws Exception {
    SecondaryAuthorizationFilter filter =
        new SecondaryAuthorizationFilter(
            token -> {
              throw new JwtException("invalid token");
            });
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Authorization", "invalid-token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, (ignoredRequest, ignoredResponse) -> {});

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void givenNonJwtAuthentication_whenFiltered_thenReturnsUnauthorized() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "credentials"));
    SecondaryAuthorizationFilter filter =
        new SecondaryAuthorizationFilter(token -> jwt(token).build());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Authorization", "x-authorization-token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, (ignoredRequest, ignoredResponse) -> {});

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void givenEitherTokenHasNoOid_whenFiltered_thenReturnsUnauthorized() throws Exception {
    for (boolean missingAuthorizationOid : List.of(true, false)) {
      Jwt.Builder oboJwt = jwt("obo-token");
      Jwt.Builder authorizationJwt =
          jwt("x-authorization-token").claim("LAA_ACCOUNTS", List.of("ABC123"));
      if (missingAuthorizationOid) {
        authorizationJwt.claim("oid", " ");
      } else {
        oboJwt.claim("oid", " ");
      }
      SecurityContextHolder.getContext()
          .setAuthentication(new JwtAuthenticationToken(oboJwt.build()));
      SecondaryAuthorizationFilter filter =
          new SecondaryAuthorizationFilter(token -> authorizationJwt.build());
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("X-Authorization", "x-authorization-token");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilterInternal(request, response, (ignoredRequest, ignoredResponse) -> {});

      assertThat(response.getStatus()).isEqualTo(401);
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void givenSecondaryAuthorizationWithoutEntitlements_whenFiltered_thenContinuesWithoutMerging()
      throws Exception {
    Jwt oboJwt = jwt("obo-token").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(oboJwt));
    SecondaryAuthorizationFilter filter =
        new SecondaryAuthorizationFilter(token -> jwt(token).build());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Authorization", "x-authorization-token");
    AtomicBoolean chainInvoked = new AtomicBoolean();

    filter.doFilterInternal(
        request,
        new MockHttpServletResponse(),
        (ignoredRequest, ignoredResponse) -> chainInvoked.set(true));

    assertThat(chainInvoked).isTrue();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isSameAs(oboJwt);
  }

  @Test
  void givenOboJwtWithoutIssuedAt_whenEntitlementsAreMerged_thenPreservesItsOptionalTimestamp()
      throws Exception {
    Jwt oboJwt =
        Jwt.withTokenValue("obo-token")
            .header("alg", "none")
            .claim("oid", "entra-object-id")
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(oboJwt));
    SecondaryAuthorizationFilter filter =
        new SecondaryAuthorizationFilter(
            token -> jwt(token).claim("LAA_ACCOUNTS", List.of("ABC123")).build());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Authorization", "x-authorization-token");

    filter.doFilterInternal(
        request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> {});

    JwtAuthenticationToken authentication =
        (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getToken().getIssuedAt()).isNull();
  }

  private Jwt.Builder jwt(String tokenValue) {
    Instant issuedAt = Instant.now();
    return Jwt.withTokenValue(tokenValue)
        .header("alg", "none")
        .subject("user")
        .claim("oid", "entra-object-id")
        .issuedAt(issuedAt)
        .expiresAt(issuedAt.plusSeconds(300));
  }
}

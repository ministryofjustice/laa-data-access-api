package uk.gov.justice.laa.dstew.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedCaseworkerIdTest {
  private final AuthenticatedCaseworkerId authenticatedCaseworkerId =
      new AuthenticatedCaseworkerId();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void returnsTheAuthenticatedUuidOid() {
    UUID oid = UUID.randomUUID();
    authenticate(oid.toString());

    assertThat(authenticatedCaseworkerId.get()).isEqualTo(oid);
  }

  @Test
  void rejectsMissingOrBlankOid() {
    assertThatThrownBy(() -> authenticatedCaseworkerId.get())
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("An Entra OID is required");
    authenticate(" ");
    assertThatThrownBy(() -> authenticatedCaseworkerId.get())
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("An Entra OID is required");
  }

  @Test
  void rejectsMalformedOidAsAnAuthorizationFailure() {
    authenticate("not-a-uuid");

    assertThatThrownBy(() -> authenticatedCaseworkerId.get())
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("The Entra OID must be a UUID");
  }

  private void authenticate(String oid) {
    Instant issuedAt = Instant.now();
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("user")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(300))
            .claim("oid", oid)
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }
}

package uk.gov.justice.laa.dstew.access.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtValidationTest {

  private static final String ISSUER = "https://login.microsoftonline.com/tenant/v2.0";

  @Test
  void givenJwtWithoutExpiry_whenValidated_thenRejectsIt() {
    var result = JwtValidation.requiringExpiryWithIssuer(ISSUER).validate(jwt().build());

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors())
        .anyMatch(error -> Objects.equals(error.getDescription(), "exp is required"));
  }

  @Test
  void givenJwtThatIsExpired_whenValidated_thenRejectsIt() {
    var result =
        JwtValidation.requiringExpiryWithIssuer(ISSUER)
            .validate(jwt().expiresAt(Instant.now().minusSeconds(300)).build());

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors())
        .anyMatch(error -> Objects.equals(error.getErrorCode(), "invalid_token"));
  }

  @Test
  void givenJwtWithFutureExpiry_whenValidated_thenAcceptsIt() {
    var result =
        JwtValidation.requiringExpiryWithIssuer(ISSUER)
            .validate(jwt().expiresAt(Instant.now().plusSeconds(300)).build());

    assertThat(result.hasErrors()).isFalse();
  }

  private Jwt.Builder jwt() {
    return Jwt.withTokenValue("token").header("alg", "none").issuer(ISSUER).subject("user");
  }
}

package uk.gov.justice.laa.dstew.access.utils;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jwt.SignedJWT;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.nav.security.mock.oauth2.MockOAuth2Server;
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback;

/**
 * Mints real signed JWTs using the mock OAuth2 server's internal signing key. These tokens exercise
 * the full SecurityConfig validation chain: signature verification, issuer check, audience check,
 * and role extraction.
 *
 * <p>Role handling: SecurityConfig's jwtAuthenticationConverter grants the API caseworker app role
 * to authenticated JWTs. Unknown-role integration tests therefore use an authenticated token with a
 * blank subject so @AllowApiCaseworker fails the @entra.hasName() check and returns 403 Forbidden.
 */
public class TestTokenFactory {

  private static final String ISSUER_ID = "entra";
  private static final String AUDIENCE = "laa-data-access-api";

  private final MockOAuth2Server server;

  public TestTokenFactory(MockOAuth2Server server) {
    this.server = server;
  }

  /**
   * Token with the APPROLE_LAA_CASEWORKER authority. The "roles" claim contains "LAA_CASEWORKER"
   * which, with the APPROLE_ prefix, becomes APPROLE_LAA_CASEWORKER. LAA_APP_ROLES is present to
   * pass the audience validator.
   */
  public String caseworkerToken() {
    DefaultOAuth2TokenCallback callback =
        new DefaultOAuth2TokenCallback(
            ISSUER_ID,
            "test-user-" + UUID.randomUUID(),
            JOSEObjectType.JWT.getType(),
            List.of(AUDIENCE),
            Map.of("roles", "LAA_CASEWORKER", "LAA_APP_ROLES", "LAA_CASEWORKER"),
            3600);
    SignedJWT jwt = server.issueToken(ISSUER_ID, UUID.randomUUID().toString(), callback);
    return jwt.serialize();
  }

  /**
   * Authenticated token used by forbidden-role tests. SecurityConfig grants APPROLE_LAA_CASEWORKER
   * to authenticated JWTs, so this uses a blank subject to fail @entra.hasName() while still
   * exercising the signed JWT validation chain.
   */
  public String unknownRoleToken() {
    DefaultOAuth2TokenCallback callback =
        new DefaultOAuth2TokenCallback(
            ISSUER_ID,
            "",
            JOSEObjectType.JWT.getType(),
            List.of(AUDIENCE),
            Map.of("roles", "UNKNOWN", "LAA_APP_ROLES", "UNKNOWN"),
            3600);
    SignedJWT jwt = server.issueToken(ISSUER_ID, UUID.randomUUID().toString(), callback);
    return jwt.serialize();
  }

  /**
   * Token with no LAA_APP_ROLES — should be rejected by the audience validator (which requires both
   * the correct audience AND non-null LAA_APP_ROLES claim).
   */
  public String tokenWithNoRoles() {
    DefaultOAuth2TokenCallback callback =
        new DefaultOAuth2TokenCallback(
            ISSUER_ID,
            "test-user-no-roles",
            JOSEObjectType.JWT.getType(),
            List.of(AUDIENCE),
            Map.of(),
            3600);
    SignedJWT jwt = server.issueToken(ISSUER_ID, UUID.randomUUID().toString(), callback);
    return jwt.serialize();
  }

  /** Token with wrong audience — should be rejected by jwtDecoder's audience validator. */
  public String wrongAudienceToken() {
    DefaultOAuth2TokenCallback callback =
        new DefaultOAuth2TokenCallback(
            ISSUER_ID,
            "test-user-wrong-aud",
            JOSEObjectType.JWT.getType(),
            List.of("some-other-api"),
            Map.of("LAA_APP_ROLES", "LAA_CASEWORKER"),
            3600);
    SignedJWT jwt = server.issueToken(ISSUER_ID, UUID.randomUUID().toString(), callback);
    return jwt.serialize();
  }
}

package uk.gov.justice.laa.dstew.access.security;

import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Retrieves effective identity and entitlement claims for the current request. */
public final class SecurityHelper {

  private static final String LAA_ACCOUNTS_CLAIM = "LAA_ACCOUNTS";
  private static final String ENTRA_OID_CLAIM = "oid";

  private SecurityHelper() {}

  /**
   * Gets the validated {@code LAA_ACCOUNTS} claim from the current JWT authentication.
   *
   * @return an immutable list of account codes, or an empty list when unavailable
   */
  public static List<String> getLaaAccounts() {
    List<String> accounts =
        getJwtAuthentication()
            .map(JwtAuthenticationToken::getToken)
            .map(jwt -> jwt.getClaimAsStringList(LAA_ACCOUNTS_CLAIM))
            .orElse(null);
    return accounts == null ? List.of() : List.copyOf(accounts);
  }

  /**
   * Gets the Entra object identifier from the JWT for the current request.
   *
   * @return the Entra OID, or empty when the request is not JWT-authenticated or the claim is
   *     absent
   */
  public static Optional<String> getEntraOid() {
    return getJwtAuthentication().flatMap(authentication -> getEntraOid(authentication.getToken()));
  }

  /**
   * Gets the Entra object identifier from a JWT.
   *
   * @param jwt the JWT to inspect
   * @return the Entra OID, or empty when the claim is absent or blank
   */
  public static Optional<String> getEntraOid(Jwt jwt) {
    return Optional.ofNullable(jwt.getClaimAsString(ENTRA_OID_CLAIM)).filter(oid -> !oid.isBlank());
  }

  private static Optional<JwtAuthenticationToken> getJwtAuthentication() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication instanceof JwtAuthenticationToken jwtAuthentication
        ? Optional.of(jwtAuthentication)
        : Optional.empty();
  }
}

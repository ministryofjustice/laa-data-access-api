package uk.gov.justice.laa.dstew.access.security;

import java.util.Arrays;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/** Authorizes HTTP application-read requests for callers with a supported delegated scope. */
public class ApplicationReadScopeAuthorizationManager
    implements AuthorizationManager<RequestAuthorizationContext> {

  public static final String SCOPES_CLAIM = "scp";
  public static final String ACCESS_AS_PROVIDER = "access_as_provider";
  public static final String ACCESS_AS_USER = "access_as_user";

  @Override
  public AuthorizationResult authorize(
      Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
    Authentication currentAuthentication = authentication.get();
    boolean granted =
        currentAuthentication instanceof JwtAuthenticationToken jwtAuthentication
            && hasSupportedScope(jwtAuthentication);
    return new AuthorizationDecision(granted);
  }

  private boolean hasSupportedScope(JwtAuthenticationToken authentication) {
    String scopes = authentication.getToken().getClaimAsString(SCOPES_CLAIM);
    return scopes != null
        && Arrays.stream(scopes.split("\\s+"))
            .anyMatch(scope -> ACCESS_AS_USER.equals(scope) || ACCESS_AS_PROVIDER.equals(scope));
  }
}

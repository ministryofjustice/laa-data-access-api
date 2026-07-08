package uk.gov.justice.laa.dstew.access.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/** Spring configuration to allow dev tokens. */
@Configuration
@ConditionalOnProperty(prefix = "feature", name = "enable-dev-token", havingValue = "true")
@ExcludeFromGeneratedCodeCoverage
public class DevTokenConfig {

  private static final Map<String, List<String>> DEV_TOKENS =
      Map.of(
          "swagger-caseworker-token",
          List.of("APPROLE_LAA_CASEWORKER"),
          "unknown-token",
          List.of("APPROLE_UNKNOWN"));

  private static final Logger log = LoggerFactory.getLogger(DevTokenConfig.class);

  /**
   * Log dev token status on startup to make it clear when dev tokens are enabled and what
   * environment variables are present.
   */
  public DevTokenConfig() {
    log.info("DevTokenConfig enabled: dev tokens are available in this environment.");
  }

  /**
   * Filter that checks for dev tokens and injects Authentication if a valid token is found. This
   * allows developers to easily authenticate as different roles without needing real JWTs.
   *
   * @return a filter that injects Authentication for valid dev tokens
   */
  @Bean
  @ExcludeFromGeneratedCodeCoverage
  public OncePerRequestFilter devTokenFilter() {
    return new DevTokenFilter();
  }

  @ExcludeFromGeneratedCodeCoverage
  private static class DevTokenFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

      // Extract bearer token
      String authHeader = request.getHeader(AUTHORIZATION_HEADER);
      if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
        filterChain.doFilter(request, response);
        return;
      }

      String token = authHeader.substring(BEARER_PREFIX.length());

      // Only allow explicitly configured tokens
      if (!DEV_TOKENS.containsKey(token)) {
        filterChain.doFilter(request, response);
        return;
      }

      // Inject role-based Authentication
      List<SimpleGrantedAuthority> authorities =
          DEV_TOKENS.get(token).stream().map(SimpleGrantedAuthority::new).toList();

      Authentication auth = new UsernamePasswordAuthenticationToken("dev-user", null, authorities);

      SecurityContextHolder.getContext().setAuthentication(auth);

      // Continue with the Authorization header removed so the downstream
      // BearerTokenAuthenticationFilter never tries to decode this dev token as a real JWT
      // (which would fail with "An error occurred while attempting to decode the Jwt: Malformed
      // token"). This guarantees the bypass works regardless of BearerTokenResolver wiring.
      filterChain.doFilter(new AuthorizationHeaderRemovingRequestWrapper(request), response);
    }
  }

  /**
   * Request wrapper that hides the {@code Authorization} header from downstream filters. Used after
   * a dev token has been authenticated so the OAuth2 resource server does not attempt to decode it
   * as a JWT.
   */
  @ExcludeFromGeneratedCodeCoverage
  private static class AuthorizationHeaderRemovingRequestWrapper extends HttpServletRequestWrapper {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    AuthorizationHeaderRemovingRequestWrapper(HttpServletRequest request) {
      super(request);
    }

    @Override
    public String getHeader(String name) {
      if (AUTHORIZATION_HEADER.equalsIgnoreCase(name)) {
        return null;
      }
      return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      if (AUTHORIZATION_HEADER.equalsIgnoreCase(name)) {
        return Collections.emptyEnumeration();
      }
      return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      List<String> names =
          Collections.list(super.getHeaderNames()).stream()
              .filter(name -> !AUTHORIZATION_HEADER.equalsIgnoreCase(name))
              .toList();
      return Collections.enumeration(names);
    }
  }
}

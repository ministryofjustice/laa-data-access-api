package uk.gov.justice.laa.dstew.access.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring configuration to allow dev tokens.
 */
@Configuration
@Profile("dev-tokens")
public class DevTokenConfig {

  private static final Map<String, List<String>> DEV_TOKENS = Map.of(
      "swagger-caseworker-token",
      List.of("APPROLE_LAA_CASEWORKER")
  );

  private boolean devTokensAllowed() {

    boolean runningInK8s =
        System.getenv("KUBERNETES_SERVICE_HOST") != null;

    if (!runningInK8s) {
      // Local / CI / integration tests
      return true;
    }

    // Running inside Kubernetes so enforce namespace check
    String namespace = System.getenv("K8S_NAMESPACE");

    return "laa-data-access-api-uat".equals(namespace) || "laa-data-access-api-staging".equals(namespace);
  }

  /**
   * Filter that checks for dev tokens and injects Authentication if a valid token is found.
   * This allows developers to easily authenticate as different roles without needing real JWTs.
   *
   * @return a filter that injects Authentication for valid dev tokens
   */
  @Bean
  public OncePerRequestFilter devTokenFilter() {
    return new OncePerRequestFilter() {

      @Override
      protected void doFilterInternal(
          HttpServletRequest request,
          HttpServletResponse response,
          FilterChain filterChain)
          throws ServletException, IOException {

        if (!devTokensAllowed()) {
          filterChain.doFilter(request, response);
          return;
        }

        // 2️⃣ Extract bearer token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
          filterChain.doFilter(request, response);
          return;
        }

        String token = authHeader.substring(7);

        // 3️⃣ Only allow explicitly configured tokens
        if (!DEV_TOKENS.containsKey(token)) {
          filterChain.doFilter(request, response);
          return;
        }

        // 4️⃣ Inject role-based Authentication
        List<SimpleGrantedAuthority> authorities =
            DEV_TOKENS.get(token).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        Authentication auth =
            new UsernamePasswordAuthenticationToken(
                "dev-user",
                null,
                authorities
            );

        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
      }
    };
  }
}

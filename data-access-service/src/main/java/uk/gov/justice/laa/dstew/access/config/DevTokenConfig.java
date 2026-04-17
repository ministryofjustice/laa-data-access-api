package uk.gov.justice.laa.dstew.access.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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

/** Spring configuration to allow dev tokens. */
@Configuration
@ConditionalOnProperty(prefix = "feature", name = "enable-dev-token", havingValue = "true")
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
  public OncePerRequestFilter devTokenFilter() {
    return new OncePerRequestFilter() {

      @Override
      protected void doFilterInternal(
          HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
          throws ServletException, IOException {

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
            DEV_TOKENS.get(token).stream().map(SimpleGrantedAuthority::new).toList();

        Authentication auth =
            new UsernamePasswordAuthenticationToken("dev-user", null, authorities);

        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
      }
    };
  }
}

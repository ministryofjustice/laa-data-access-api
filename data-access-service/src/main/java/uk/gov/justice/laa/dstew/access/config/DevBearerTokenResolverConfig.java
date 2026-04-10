package uk.gov.justice.laa.dstew.access.config;

import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Spring configuration to allow certain dev tokens to bypass the JWT filter and be handled by the
 * DevTokenFilter instead.
 */
@Configuration
@ConditionalOnProperty(prefix = "feature", name = "enable-dev-token", havingValue = "true")
public class DevBearerTokenResolverConfig {

  private static final Set<String> DEV_TOKENS = Set.of("swagger-caseworker-token", "unknown-token");

  /**
   * Custom BearerTokenResolver that checks for dev tokens and returns null if a dev token is found,
   * allowing the DevTokenFilter to handle it instead.
   *
   * @return a BearerTokenResolver that returns null for dev tokens
   */
  @Bean
  public BearerTokenResolver bearerTokenResolver() {
    return request -> {
      String authHeader = request.getHeader("Authorization");

      if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        return null;
      }

      String token = authHeader.substring(7);

      // If it is a dev token, do NOT let JWT filter see it
      if (DEV_TOKENS.contains(token)) {
        return null;
      }

      // Otherwise return it normally (real JWT)
      return token;
    };
  }
}

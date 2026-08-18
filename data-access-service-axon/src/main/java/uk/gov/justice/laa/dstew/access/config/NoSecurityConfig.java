package uk.gov.justice.laa.dstew.access.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

/**
 * Permissive security filter chain used when {@code feature.disable-security=true}.
 *
 * <p>Intended for local development only and temporary while the flag remains supported.
 */
@ExcludeFromGeneratedCodeCoverage
@ConditionalOnProperty(prefix = "feature", name = "disable-security", havingValue = "true")
@Configuration
class NoSecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(NoSecurityConfig.class);

  NoSecurityConfig() {
    log.info("NoSecurityConfig enabled: security filter chain disabled.");
  }

  @Bean
  WebSecurityCustomizer webSecurityCustomizer() {
    return web -> web.ignoring().requestMatchers("/**");
  }

  @Bean("entra")
  EffectiveAuthorizationProvider authProvider() {
    return new EffectiveAuthorizationProvider() {
      @Override
      public boolean hasAppRole(String name) {
        return true;
      }

      @Override
      public boolean hasAnyAppRole(String... names) {
        return true;
      }

      @Override
      public boolean hasName() {
        return true;
      }
    };
  }
}

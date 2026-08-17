package uk.gov.justice.laa.dstew.access.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
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
  SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
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

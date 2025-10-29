package uk.gov.justice.laa.dstew.access.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

/**
 * Spring Security configuration if security is disabled (e.g. for development).
 */
@ConditionalOnProperty(prefix = "feature", name = "disable-security", havingValue = "true")
@Configuration
class NoSecurityConfig {
  /**
   * Return the security filter chain.
   *
   * @param http Used to configure web security.
   * @return The built security configuration.
   * @throws Exception if anything went wrong.
   */
  @Bean
  SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(authorize -> authorize
            .anyRequest().permitAll())
        //.oauth2ResourceServer(AbstractHttpConfigurer::disable)
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
    };
  }
}

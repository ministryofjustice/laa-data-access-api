package uk.gov.justice.laa.dstew.access.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.azure.spring.cloud.autoconfigure.implementation.aad.security.AadResourceServerHttpSecurityConfigurer;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

/**
 * Spring Security configuration if security is not disabled.
 */
@ConditionalOnProperty(prefix = "feature", name = "disable-security", havingValue = "false", matchIfMissing = true)
@Configuration
@EnableMethodSecurity
@EnableWebSecurity
class SecurityConfig {
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
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
            .anyRequest().authenticated())
        .with(AadResourceServerHttpSecurityConfigurer.aadResourceServer(), withDefaults())
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }

  @Bean("entra")
  EffectiveAuthorizationProvider authProvider() {
    return new EffectiveAuthorizationProvider() {
      @Override
      public boolean hasAppRole(String name) {
        return getAuthorities().contains("APPROLE_" + name);
      }

      @Override
      public boolean hasAnyAppRole(String... names) {
        final var authorities = getAuthorities();
        return Arrays.stream(names)
            .anyMatch(name -> authorities.contains("APPROLE_" + name));
      }

      private Set<String> getAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toUnmodifiableSet()) : Set.of();
      }
    };
  }
}

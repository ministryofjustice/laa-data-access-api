package uk.gov.justice.laa.dstew.access.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

/**
 * Enables Spring method-level security so {@code @PreAuthorize} annotations are enforced.
 *
 * <p>Temporary while {@code feature.disable-security} is still supported.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "feature",
    name = "disable-security",
    havingValue = "false",
    matchIfMissing = true)
@EnableMethodSecurity
public class MethodSecurityConfig {

  private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";
  private static final String APP_ROLE_AUTHORITY_PREFIX = "APPROLE_";

  @Bean("entra")
  EffectiveAuthorizationProvider authProvider() {
    return new SecurityContextEffectiveAuthorizationProvider();
  }

  private static final class SecurityContextEffectiveAuthorizationProvider
      implements EffectiveAuthorizationProvider {
    @Override
    public boolean hasAppRole(String name) {
      Set<String> authorities = getAuthorities();
      return authorities.contains(ROLE_AUTHORITY_PREFIX + name)
          || authorities.contains(APP_ROLE_AUTHORITY_PREFIX + name);
    }

    @Override
    public boolean hasAnyAppRole(String... names) {
      return Arrays.stream(names).anyMatch(this::hasAppRole);
    }

    @Override
    public boolean hasName() {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      return auth != null
          && auth.isAuthenticated()
          && auth.getName() != null
          && !auth.getName().isBlank();
    }

    private Set<String> getAuthorities() {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      return (auth != null && auth.isAuthenticated())
          ? auth.getAuthorities().stream()
              .map(GrantedAuthority::getAuthority)
              .collect(Collectors.toUnmodifiableSet())
          : Set.of();
    }
  }
}

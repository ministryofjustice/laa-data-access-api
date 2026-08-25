package uk.gov.justice.laa.dstew.access.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.shared.security.EffectiveAuthorizationProvider;

/**
 * Provides a permissive method-authorization helper only when security is explicitly disabled. HTTP
 * security is disabled separately via profile auto-configuration exclusions.
 */
@ExcludeFromGeneratedCodeCoverage
@ConditionalOnProperty(prefix = "feature", name = "disable-security", havingValue = "true")
@Configuration
class DisabledSecurityAuthorizationBypassConfig {

  private static final Logger log =
      LoggerFactory.getLogger(DisabledSecurityAuthorizationBypassConfig.class);

  /** Log disabled-security mode on startup to make it clear authorization checks are bypassed. */
  public DisabledSecurityAuthorizationBypassConfig() {
    log.info(
        "DisabledSecurityAuthorizationBypassConfig enabled: method authorization bypass active.");
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

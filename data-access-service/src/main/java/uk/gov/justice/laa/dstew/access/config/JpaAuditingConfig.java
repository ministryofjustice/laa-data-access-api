package uk.gov.justice.laa.dstew.access.config;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Configuration for JPA auditing (automatic created & lastModified metadata).
 * Configuration can be disabled for AccessAppTests.contextLoads().
 */
@ConditionalOnProperty(prefix = "feature", name = "disable-jpa-auditing", havingValue = "false", matchIfMissing = true)
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
class JpaAuditingConfig {
  /**
   * Auditor provider implementation for the application.
   *
   * @return provider of current username.
   */
  @Bean("auditorProvider")
  AuditorAware<String> auditorProvider() {
    return () -> Optional.of(SecurityContextHolder.getContext())
              .map(SecurityContext::getAuthentication)
              .filter(Authentication::isAuthenticated)
              .map(Authentication::getName);
  }
}

package uk.gov.justice.laa.dstew.access.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Feature flag implementation using Spring properties named `feature.*`.
 *
 * <p>Feature flags are boolean-valued and each flag has a default value of
 * `false` if both the Spring property and the environment variable are unset.
 * You can either set the Spring property in `application.yml` or set the
 * environment variable in the process environment to enable a feature.
 *
 * <p>Feature flags default to `false`, which should be the "fail-safe" value.
 * So `false` should be the safer, more-secure or more-usual feature state.
 *
 * <p>Using `@ConfigurationProperties` annotation like this requires the
 * `@ConfigurationPropertiesScan` or similar on the `SpringApplication` class.
 * Alternatively, you can use `@ConditionalOnProperty` without this class.
 *
 * @param enableDevToken whether OAuth2 authorization allows dev tokens.
 *        Spring property: `feature.enable-dev-token`.
 *        Environment variable: `FEATURE_ENABLE_DEV_TOKEN`.
 * @param disableJpaAuditing whether JPA auditing is disabled.
 *        Spring property: `feature.disable-jpa-auditing`.
 *        Environment variable: `FEATURE_DISABLEJPAAUDITING`.
 *        Needed because JPA auditing cannot be disabled by excluding
 *        autoconfiguration classes in `@SpringBootTest` tests.
 * @param disableSecurity whether Spring Security is disabled (e.g. for development).
 *        Spring property: `feature.disable-security`.
 *        Environment variable: `FEATURE_DISABLE_SECURITY`.
 */
@ExcludeFromGeneratedCodeCoverage
@ConfigurationProperties(prefix = "feature")
public record FeatureProperties(boolean enableDevToken,
                                boolean disableJpaAuditing,
                                boolean disableSecurity) {
}

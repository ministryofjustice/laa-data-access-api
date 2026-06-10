package uk.gov.justice.laa.dstew.access.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Configuration properties for the flagd feature flag provider. These properties are used to
 * configure the connection to the flagd server, including whether the provider is enabled, the
 * host, and the port.
 */
@ExcludeFromGeneratedCodeCoverage
@ConfigurationProperties(prefix = "flagd")
public record FlagdProperties(boolean enabled, String host, int port) {}

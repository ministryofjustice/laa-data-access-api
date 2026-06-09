package uk.gov.justice.laa.dstew.access.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flagd")
public record FlagdProperties(boolean enabled, String host, int port) {}




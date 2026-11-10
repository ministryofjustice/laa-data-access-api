package uk.gov.justice.laa.dstew.access.bdd.config;

import java.time.Duration;

public final class BddConfiguration {
  private final String baseUrl;
  private final String token;
  private final String serviceName;
  private final String schemaVersion;
  private final Duration timeout;
  private final Duration pollInterval;

  public BddConfiguration() {
    baseUrl = value("LAA_BDD_BASE_URL", "http://localhost:9000");
    token = required("LAA_BDD_TOKEN");
    serviceName = value("LAA_BDD_SERVICE_NAME", "CIVIL_APPLY");
    schemaVersion = value("LAA_BDD_SCHEMA_VERSION", "1");
    timeout = Duration.ofSeconds(Long.parseLong(value("LAA_BDD_TIMEOUT", "30")));
    pollInterval = Duration.ofMillis(Long.parseLong(value("LAA_BDD_POLL_INTERVAL", "250")));
  }

  public String baseUrl() {
    return baseUrl;
  }

  public String token() {
    return token;
  }

  public String serviceName() {
    return serviceName;
  }

  public String schemaVersion() {
    return schemaVersion;
  }

  public Duration timeout() {
    return timeout;
  }

  public Duration pollInterval() {
    return pollInterval;
  }

  private static String required(String name) {
    return value(name, null);
  }

  private static String value(String name, String defaultValue) {
    String value = System.getenv().getOrDefault(name, defaultValue);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must be set for BDD API tests");
    }
    return value;
  }
}

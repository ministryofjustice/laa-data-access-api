package uk.gov.justice.laa.dstew.access.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class AxonProfileConfigurationTest {

  private static final String AXON_DIALECT =
      "uk.gov.justice.laa.dstew.access.config.ByteaEnforcedPostgresSqlDialect";

  @Test
  void previewProfileUsesPreviewEnvironmentSettingsAndPreservesAxonPersistence()
      throws IOException {
    List<PropertySource<?>> properties = load("application-preview.yaml");

    assertThat(property(properties, "spring.application.name"))
        .isEqualTo("data-access-service-axon");
    assertThat(property(properties, "spring.datasource.url")).isEqualTo("${DB_URL}");
    assertThat(property(properties, "spring.datasource.username")).isEqualTo("postgres");
    assertAxonPersistence(properties);
    assertEnvironmentIntegrationProperties(properties);
  }

  @Test
  void mainProfileUsesMainEnvironmentSettingsAndPreservesAxonPersistence() throws IOException {
    List<PropertySource<?>> properties = load("application-main.yml");

    assertThat(property(properties, "spring.application.name"))
        .isEqualTo("data-access-service-axon");
    assertThat(property(properties, "spring.datasource.url")).isEqualTo("${DB_URL}");
    assertThat(property(properties, "spring.datasource.username")).isEqualTo("${DB_USERNAME}");
    assertAxonPersistence(properties);
    assertEnvironmentIntegrationProperties(properties);
    assertThat(property(properties, "jdbc.datasource-proxy.slow-query.threshold"))
        .isEqualTo("${SLOW_QUERY_THRESHOLD_SECONDS:1}");
  }

  @Test
  void unsecuredProfileDisablesSecurityAndPreservesAxonPersistence() throws IOException {
    List<PropertySource<?>> properties = load("application-unsecured.yml");

    assertThat(property(properties, "spring.application.name"))
        .isEqualTo("data-access-service-axon");
    assertThat(property(properties, "spring.datasource.url")).isEqualTo("${DB_URL}");
    assertThat(property(properties, "spring.datasource.username")).isEqualTo("${DB_USERNAME}");
    assertAxonPersistence(properties);
    assertEnvironmentIntegrationProperties(properties);
    assertThat(property(properties, "feature.disable-security")).isEqualTo(true);
    assertThat(property(properties, "server.port")).isEqualTo("${SERVER_PORT:8082}");
  }

  private static void assertAxonPersistence(List<PropertySource<?>> properties) {
    assertThat(property(properties, "spring.jpa.database-platform")).isEqualTo(AXON_DIALECT);
    assertThat(property(properties, "spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(property(properties, "spring.jpa.properties.hibernate.default_schema"))
        .isEqualTo("${AXON_DB_SCHEMA:axon}");
    assertThat(property(properties, "axon.db.schema")).isEqualTo("${AXON_DB_SCHEMA:axon}");
    assertThat(property(properties, "axon.db.flyway-table"))
        .isEqualTo("${AXON_FLYWAY_TABLE:flyway_schema_history}");
  }

  private static void assertEnvironmentIntegrationProperties(List<PropertySource<?>> properties) {
    assertThat(property(properties, "spring.cloud.aws.region")).isEqualTo("${AWS_REGION}");
    assertThat(property(properties, "spring.security.oauth2.resourceserver.jwt.issuer-uri"))
        .isEqualTo("${ENTRA_ISSUER_URI}");
    assertThat(property(properties, "spring.security.oauth2.resourceserver.jwt.jwk-set-uri"))
        .isEqualTo("${ENTRA_JWK_SET_URI}");
    assertThat(property(properties, "spring.security.oauth2.resourceserver.jwt.audience"))
        .isEqualTo("${ENTRA_AUD}");
  }

  private static List<PropertySource<?>> load(String resource) throws IOException {
    return new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource));
  }

  private static Object property(List<PropertySource<?>> properties, String name) {
    return properties.stream()
        .map(propertySource -> propertySource.getProperty(name))
        .filter(value -> value != null)
        .findFirst()
        .orElse(null);
  }
}

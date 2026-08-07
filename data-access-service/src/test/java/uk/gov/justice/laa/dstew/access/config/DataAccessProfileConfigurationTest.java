package uk.gov.justice.laa.dstew.access.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class DataAccessProfileConfigurationTest {

  @Test
  void previewProfileUsesEphemeralPostgresAndSharedConfiguration() throws IOException {
    List<PropertySource<?>> properties = loadProfile("application-preview.yaml");

    assertSharedConfiguration(properties);
    assertThat(property(properties, "spring.datasource.username")).isEqualTo("postgres");
  }

  @Test
  void mainProfileUsesEnvironmentDatabaseCredentialsAndSlowQueryThreshold() throws IOException {
    List<PropertySource<?>> properties = loadProfile("application-main.yml");

    assertSharedConfiguration(properties);
    assertThat(property(properties, "spring.datasource.username"))
        .isEqualTo("${DB_USERNAME:laa_user}");
    assertThat(property(properties, "jdbc.datasource-proxy.slow-query.threshold"))
        .isEqualTo("${SLOW_QUERY_THRESHOLD_SECONDS:1}");
  }

  @Test
  void unsecuredProfileUsesSharedConfigurationAndDisablesSecurity() throws IOException {
    List<PropertySource<?>> properties = loadProfile("application-unsecured.yml");

    assertSharedConfiguration(properties);
    assertThat(property(properties, "spring.datasource.username"))
        .isEqualTo("${DB_USERNAME:laa_user}");
    assertThat(property(properties, "feature.disable-security")).isEqualTo(true);
  }

  private static void assertSharedConfiguration(List<PropertySource<?>> properties) {
    assertThat(property(properties, "spring.application.name"))
        .isEqualTo("LAA Data Stewardship Access - Access Application Data");
    assertThat(property(properties, "spring.datasource.url"))
        .isEqualTo("${DB_URL:jdbc:postgresql://localhost:5432/laa_data_access_api}");
    assertThat(property(properties, "spring.datasource.password"))
        .isEqualTo("${DB_PASSWORD:laa_password}");
    assertThat(property(properties, "spring.jpa.database-platform"))
        .isEqualTo("org.hibernate.dialect.PostgreSQLDialect");
    assertThat(property(properties, "spring.cloud.aws.region"))
        .isEqualTo("${AWS_REGION:eu-west-2}");
    assertThat(property(properties, "management.endpoint.health.show-details")).isEqualTo("always");
  }

  private static List<PropertySource<?>> loadProfile(String resource) throws IOException {
    YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
    List<PropertySource<?>> properties =
        new ArrayList<>(loader.load(resource, new ClassPathResource(resource)));
    properties.addAll(loader.load("application.yml", new ClassPathResource("application.yml")));
    return properties;
  }

  private static Object property(List<PropertySource<?>> properties, String name) {
    return properties.stream()
        .map(propertySource -> propertySource.getProperty(name))
        .filter(value -> value != null)
        .findFirst()
        .orElse(null);
  }
}

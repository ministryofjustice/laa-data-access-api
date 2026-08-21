package uk.gov.justice.laa.dstew.access.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.sql.init.dependency.DatabaseInitializationDependencyConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Runs the Axon schema migrations without Flyway's configuration-properties converter.
 *
 * <p>Spring Boot 4.1's Flyway converter is discovered while configuration properties are being
 * bound. Axon's Jackson converter requires the Jackson properties from that same binder, producing
 * a bootstrap cycle. The application uses a fixed migration location and schema, so programmatic
 * Flyway configuration avoids that converter while retaining database-initialization ordering.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
@Import(DatabaseInitializationDependencyConfigurer.class)
@ExcludeFromGeneratedCodeCoverage
public class ApplicationFlywayConfig {

  @Value("${axon.db.schema:axon}")
  private String schema;

  @Value("${axon.db.flyway-table:flyway_schema_history}")
  private String flywayTable;

  @Bean
  Flyway flyway(DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .createSchemas(true)
        .defaultSchema(schema)
        .schemas(schema)
        .table(flywayTable)
        .locations("classpath:db/migration")
        .load();
  }

  @Bean
  FlywayMigrationInitializer flywayMigrationInitializer(Flyway flyway) {
    return new FlywayMigrationInitializer(flyway);
  }
}

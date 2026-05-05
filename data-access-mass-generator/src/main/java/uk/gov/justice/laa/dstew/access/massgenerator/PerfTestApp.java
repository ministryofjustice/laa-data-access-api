package uk.gov.justice.laa.dstew.access.massgenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot entry point for the API performance test runner.
 *
 * <p>Run with:
 *
 * <pre>
 *   java -jar laa-data-access-api-mass-generator.jar \
 *        --spring.main.web-application-type=none \
 *        --spring.profiles.active=perf \
 *        --perf.base-url=https://my-api.example.com \
 *        --perf.iterations=500 \
 *        --perf.concurrency=10 \
 *        --perf.bearer-token=eyJ...
 * </pre>
 *
 * <p>No database connection is required; only the API and a valid bearer token are needed.
 */
@SpringBootApplication(scanBasePackages = {})
@ImportAutoConfiguration({
  JacksonAutoConfiguration.class,
})
@ComponentScan(
    basePackages = {
      "uk.gov.justice.laa.dstew.access.massgenerator.perf",
    })
@ConfigurationPropertiesScan(
    basePackages = {
      "uk.gov.justice.laa.dstew.access.massgenerator.perf",
    })
public class PerfTestApp {

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(PerfTestApp.class);
    app.setWebApplicationType(WebApplicationType.NONE);
    System.exit(SpringApplication.exit(app.run(args)));
  }
}

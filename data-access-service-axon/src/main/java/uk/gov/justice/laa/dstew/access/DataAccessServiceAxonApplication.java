package uk.gov.justice.laa.dstew.access;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Starts the standalone Axon-based data access proof of concept. */
@ExcludeFromGeneratedCodeCoverage
@SpringBootApplication(exclude = FlywayAutoConfiguration.class)
@EnableCaching
@EnableScheduling
@EntityScan(
    basePackages = {
      "uk.gov.justice.laa.dstew.access",
      "org.axonframework.eventsourcing.eventstore.jpa",
      "org.axonframework.messaging.eventhandling.processing.streaming.token.store.jpa"
    })
public class DataAccessServiceAxonApplication {

  public static void main(String[] args) {
    SpringApplication.run(DataAccessServiceAxonApplication.class, args);
  }
}

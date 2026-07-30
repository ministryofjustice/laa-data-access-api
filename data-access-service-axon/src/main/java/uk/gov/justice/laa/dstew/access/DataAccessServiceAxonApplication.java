package uk.gov.justice.laa.dstew.access;

import org.axonframework.extension.springboot.autoconfig.JacksonConverterAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationData;
import uk.gov.justice.laa.dstew.access.command.caseworker.Caseworker;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;

/** Starts the standalone Axon-based data access proof of concept. */
@SpringBootApplication(exclude = {JacksonConverterAutoConfiguration.class, FlywayAutoConfiguration.class})
@EntityScan(
    basePackages = {
      "org.axonframework.messaging.eventhandling.processing.streaming.token.store.jpa"
    },
    basePackageClasses = {
 org.axonframework.messaging.eventhandling.processing.streaming.token.store.jpa.TokenEntry.class,
      ApplicationReadModel.class,
      ApplicationData.class,
      Caseworker.class
    })
public class DataAccessServiceAxonApplication {

  public static void main(String[] args) {
    SpringApplication.run(DataAccessServiceAxonApplication.class, args);
  }
}

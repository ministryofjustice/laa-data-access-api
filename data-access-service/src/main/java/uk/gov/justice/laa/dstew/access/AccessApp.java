package uk.gov.justice.laa.dstew.access;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Spring Boot microservice app.
 * Note using `App` to avoid confusion with the business entity `Application`.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AccessApp {

  /**
   * The app main method.
   *
   * @param args the app arguments.
   */
  public static void main(String[] args) {
    SpringApplication.run(AccessApp.class, args);
  }
}

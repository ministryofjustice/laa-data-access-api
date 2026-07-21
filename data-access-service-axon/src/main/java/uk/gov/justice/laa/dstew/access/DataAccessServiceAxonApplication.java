package uk.gov.justice.laa.dstew.access;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the standalone Axon-based data access proof of concept. */
@SpringBootApplication
public class DataAccessServiceAxonApplication {

  public static void main(String[] args) {
    SpringApplication.run(DataAccessServiceAxonApplication.class, args);
  }
}

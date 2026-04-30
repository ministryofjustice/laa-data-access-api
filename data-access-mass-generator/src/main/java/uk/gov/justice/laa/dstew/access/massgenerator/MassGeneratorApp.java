package uk.gov.justice.laa.dstew.access.massgenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {})
@ImportAutoConfiguration({
  DataSourceAutoConfiguration.class,
  DataSourceTransactionManagerAutoConfiguration.class,
  HibernateJpaAutoConfiguration.class,
  DataJpaRepositoriesAutoConfiguration.class,
  JacksonAutoConfiguration.class,
})
@ComponentScan(
    basePackages = {
      "uk.gov.justice.laa.dstew.access.massgenerator",
      "uk.gov.justice.laa.dstew.access.entity",
      "uk.gov.justice.laa.dstew.access.repository",
      "uk.gov.justice.laa.dstew.access.convertors",
      "uk.gov.justice.laa.dstew.access.model",
      "uk.gov.justice.laa.dstew.access.utils",
      "uk.gov.justice.laa.dstew.access.deserializer",
    },
    excludeFilters =
        @ComponentScan.Filter(
            type = org.springframework.context.annotation.FilterType.REGEX,
            pattern = "uk\\.gov\\.justice\\.laa\\.dstew\\.access\\.config\\..*"))
@EnableJpaRepositories(
    basePackages = {
      "uk.gov.justice.laa.dstew.access.repository",
      "uk.gov.justice.laa.dstew.access.massgenerator.job"
    })
@EntityScan(
    basePackages = {
      "uk.gov.justice.laa.dstew.access.entity",
      "uk.gov.justice.laa.dstew.access.massgenerator.job"
    })
@ConfigurationPropertiesScan
public class MassGeneratorApp {

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(MassGeneratorApp.class);

    // Check if running in web mode (--web flag)
    boolean webMode = args.length > 0 && "--web".equals(args[0]);

    if (webMode) {
      app.setWebApplicationType(WebApplicationType.SERVLET);
      app.run(args);
    } else {
      app.setWebApplicationType(WebApplicationType.NONE);
      System.exit(SpringApplication.exit(app.run(args)));
    }
  }
}

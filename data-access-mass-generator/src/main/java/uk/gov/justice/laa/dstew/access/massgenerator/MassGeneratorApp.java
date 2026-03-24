package uk.gov.justice.laa.dstew.access.massgenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication(scanBasePackages = {})
@ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        JacksonAutoConfiguration.class,
})
@ComponentScan(basePackages = {
        "uk.gov.justice.laa.dstew.access.massgenerator",
        "uk.gov.justice.laa.dstew.access.entity",
        "uk.gov.justice.laa.dstew.access.repository",
        "uk.gov.justice.laa.dstew.access.convertors",
        "uk.gov.justice.laa.dstew.access.model",
        "uk.gov.justice.laa.dstew.access.utils",
        "uk.gov.justice.laa.dstew.access.deserializer",
})
@EnableJpaRepositories(basePackages = "uk.gov.justice.laa.dstew.access.repository")
@EntityScan(basePackages = "uk.gov.justice.laa.dstew.access.entity")
@ConfigurationPropertiesScan
public class MassGeneratorApp {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MassGeneratorApp.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        System.exit(SpringApplication.exit(app.run(args)));
    }
}

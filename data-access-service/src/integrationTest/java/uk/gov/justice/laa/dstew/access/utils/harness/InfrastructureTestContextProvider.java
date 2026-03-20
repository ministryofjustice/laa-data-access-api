package uk.gov.justice.laa.dstew.access.utils.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Properties;

public class InfrastructureTestContextProvider implements TestContextProvider {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureTestContextProvider.class);

    private final AnnotationConfigApplicationContext applicationContext;
    private final WebTestClient webTestClient;

    public InfrastructureTestContextProvider() {
        log.info("InfrastructureTestContextProvider: initialising (infrastructure mode)");

        var apiUrl = requireEnv("LAA_ACCESS_API_URL");
        var dbUrl = requireEnv("LAA_ACCESS_DB_URL");
        var dbUsername = requireEnv("LAA_ACCESS_DB_USERNAME");
        var dbPassword = requireEnv("LAA_ACCESS_DB_PASSWORD");

        log.info("InfrastructureTestContextProvider: connecting to API at {}", apiUrl);
        log.info("InfrastructureTestContextProvider: connecting to database at {}", dbUrl);

        applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.registerBean("dataSource", DataSource.class, () -> {
            var ds = new DriverManagerDataSource();
            ds.setUrl(dbUrl);
            ds.setUsername(dbUsername);
            ds.setPassword(dbPassword);
            return ds;
        });
        applicationContext.register(InfrastructureJpaConfig.class);
        log.info("InfrastructureTestContextProvider: refreshing JPA application context...");
        applicationContext.refresh();
        log.info("InfrastructureTestContextProvider: JPA application context ready");

        log.info("InfrastructureTestContextProvider: building WebTestClient targeting {}", apiUrl);
        webTestClient = WebTestClient.bindToServer()
                .baseUrl(apiUrl)
                .build();
        log.info("InfrastructureTestContextProvider: initialisation complete");
    }

    @Override
    public WebTestClient webTestClient() {
        return webTestClient;
    }

    @Override
    public <T> T getBean(Class<T> type) {
        return applicationContext.getBean(type);
    }

    @Override
    public void close() {
        log.info("InfrastructureTestContextProvider: closing JPA application context");
        applicationContext.close();
    }

    private static String requireEnv(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable '" + name + "' is not set. " +
                    "Infrastructure tests require: LAA_ACCESS_API_URL, LAA_ACCESS_DB_URL, " +
                    "LAA_ACCESS_DB_USERNAME, LAA_ACCESS_DB_PASSWORD");
        }
        return value;
    }

    @EnableJpaRepositories(basePackages = "uk.gov.justice.laa.dstew.access.repository")
    @ComponentScan(basePackages = "uk.gov.justice.laa.dstew.access.utils.generator")
    static class InfrastructureJpaConfig {

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("uk.gov.justice.laa.dstew.access.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            var props = new Properties();
            props.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            factory.setJpaProperties(props);
            return factory;
        }

        @Bean
        public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }

        @Bean
        public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
            return new NamedParameterJdbcTemplate(dataSource);
        }
    }
}

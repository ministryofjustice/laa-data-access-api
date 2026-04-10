package uk.gov.justice.laa.dstew.access.utils.harness;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.access.AccessApp;
import uk.gov.justice.laa.dstew.access.Constants;

public class IntegrationTestContextProvider implements TestContextProvider {

  private static final PostgreSQLContainer<?> postgreSQLContainer =
      new PostgreSQLContainer<>(Constants.POSTGRES_INSTANCE);

  static {
    postgreSQLContainer.start();
  }

  private final ConfigurableApplicationContext applicationContext;
  private final WebTestClient webTestClient;

  public IntegrationTestContextProvider() {

    applicationContext =
        new SpringApplicationBuilder(AccessApp.class)
            .web(org.springframework.boot.WebApplicationType.SERVLET)
            .run(
                "--server.port=0",
                "--spring.datasource.url=" + postgreSQLContainer.getJdbcUrl(),
                "--spring.datasource.username=" + postgreSQLContainer.getUsername(),
                "--spring.datasource.password=" + postgreSQLContainer.getPassword(),
                "--feature.enable-dev-token=true",
                "--feature.disable-security=false");

    int port =
        applicationContext
            .getBean(Environment.class)
            .getRequiredProperty("local.server.port", Integer.class);

    webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
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
    // applicationContext.close();
  }
}

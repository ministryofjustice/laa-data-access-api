package uk.gov.justice.laa.dstew.access.utils.harness;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.access.AccessApp;
import uk.gov.justice.laa.dstew.access.Constants;
import uk.gov.justice.laa.dstew.access.config.TokenTestConfiguration;

public class IntegrationTestContextProvider implements TestContextProvider {

  private static final PostgreSQLContainer<?> postgreSQLContainer =
      new PostgreSQLContainer<>(Constants.POSTGRES_INSTANCE);

  private static final WireMockServer wireMockServer = new WireMockServer(options().dynamicPort());

  static {
    postgreSQLContainer.start();
    wireMockServer.start();
  }

  private final ConfigurableApplicationContext applicationContext;
  private final WebTestClient webTestClient;

  public IntegrationTestContextProvider() {

    applicationContext =
        new SpringApplicationBuilder(AccessApp.class, TokenTestConfiguration.class)
            .web(org.springframework.boot.WebApplicationType.SERVLET)
            .run(
                "--server.port=0",
                "--spring.datasource.url=" + postgreSQLContainer.getJdbcUrl(),
                "--spring.datasource.username=" + postgreSQLContainer.getUsername(),
                "--spring.datasource.password=" + postgreSQLContainer.getPassword(),
                "--feature.enable-dev-token=true",
                "--feature.disable-security=false",
                "--app.sds-api.url=http://localhost:" + wireMockServer.port(),
                "--app.sds-api.bucket-name=test-bucket",
                "--app.sds-api.client-registration-id=sds-test",
                "--app.sds-api.principal-name=sds-service");

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
  @SuppressWarnings("unchecked")
  public <T> T getBean(Class<T> type) {
    if (WireMockServer.class.equals(type)) {
      return type.cast(wireMockServer);
    }
    return applicationContext.getBean(type);
  }

  @Override
  public void close() {
    // applicationContext.close();
  }
}

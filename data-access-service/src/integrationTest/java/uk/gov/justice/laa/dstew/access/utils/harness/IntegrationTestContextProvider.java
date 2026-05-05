package uk.gov.justice.laa.dstew.access.utils.harness;

import no.nav.security.mock.oauth2.MockOAuth2Server;
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

  private static final MockOAuth2Server mockOAuth2Server = new MockOAuth2Server();

  static {
    postgreSQLContainer.start();
    mockOAuth2Server.start();
  }

  private final ConfigurableApplicationContext applicationContext;
  private final WebTestClient webTestClient;

  public IntegrationTestContextProvider() {

    String issuerUrl = mockOAuth2Server.issuerUrl("entra").toString();
    String jwksUrl = mockOAuth2Server.jwksUrl("entra").toString();

    applicationContext =
        new SpringApplicationBuilder(AccessApp.class)
            .web(org.springframework.boot.WebApplicationType.SERVLET)
            .run(
                "--server.port=0",
                "--spring.datasource.url=" + postgreSQLContainer.getJdbcUrl(),
                "--spring.datasource.username=" + postgreSQLContainer.getUsername(),
                "--spring.datasource.password=" + postgreSQLContainer.getPassword(),
                "--spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuerUrl,
                "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + jwksUrl,
                "--spring.security.oauth2.resourceserver.jwt.audience=laa-data-access-api");

    int port =
        applicationContext
            .getBean(Environment.class)
            .getRequiredProperty("local.server.port", Integer.class);

    webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  /** Expose the mock server so TestTokenFactory can use it. */
  public static MockOAuth2Server mockOAuth2Server() {
    return mockOAuth2Server;
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
    mockOAuth2Server.shutdown();
  }
}

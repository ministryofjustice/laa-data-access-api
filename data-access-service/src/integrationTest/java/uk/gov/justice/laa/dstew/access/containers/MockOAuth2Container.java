package uk.gov.justice.laa.dstew.access.containers;

import java.nio.file.Paths;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import uk.gov.justice.laa.dstew.access.Constants;

/**
 * Testcontainers wrapper for {@code ghcr.io/navikt/mock-oauth2-server}.
 *
 * <p>Mounts the shared {@code infra/mock-oauth2/config.json} file directly from the project
 * filesystem. This is the same config used by docker-compose for local dev and smoke tests.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * @Container
 * static final MockOAuth2Container MOCK_OAUTH2 = new MockOAuth2Container();
 * }</pre>
 */
public class MockOAuth2Container extends GenericContainer<MockOAuth2Container> {

  private static final int PORT = 9999;
  private static final String CONFIG_CONTAINER_PATH = "/etc/mock-oauth2/config.json";

  public MockOAuth2Container() {
    super(DockerImageName.parse(Constants.MOCK_OAUTH2_SERVER_IMAGE));
    withExposedPorts(PORT);
    withEnv("SERVER_PORT", String.valueOf(PORT));
    withEnv("JSON_CONFIG_PATH", CONFIG_CONTAINER_PATH);

    // Mount shared config from project root - same file used by docker-compose
    String projectRoot = System.getProperty("user.dir"); // data-access-service directory
    String configPath =
        Paths.get(projectRoot, "..", "infra", "mock-oauth2", "config.json").toString();
    withCopyFileToContainer(MountableFile.forHostPath(configPath), CONFIG_CONTAINER_PATH);

    waitingFor(Wait.forHttp("/entra/.well-known/openid-configuration").forStatusCode(200));
  }

  /** Base URL of the running mock server, e.g. {@code http://localhost:54321}. */
  public String getBaseUrl() {
    return "http://localhost:" + getMappedPort(PORT);
  }

  /** Token endpoint for the {@code entra} issuer. */
  public String getTokenUrl() {
    return getBaseUrl() + "/entra/token";
  }

  /** JWKS endpoint for the {@code entra} issuer. */
  public String getJwksUrl() {
    return getBaseUrl() + "/entra/jwks";
  }

  /** Issuer URI — use this for {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}. */
  public String getIssuerUri() {
    return getBaseUrl() + "/entra";
  }
}

package uk.gov.justice.laa.dstew.access;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

/**
 * Boots the full service on a random port against a fresh PostgreSQL Testcontainer per test class.
 */
@Testcontainers
@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"feature.enable-dev-token=true"})
@AutoConfigureMockMvc
@AutoConfigureTestRestTemplate
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class BaseIntegrationTest {

  @Container @ServiceConnection
  protected static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort protected int port;

  @Autowired protected TestRestTemplate restTemplate;

  @Autowired protected MockMvc mockMvc;

  @Autowired protected JdbcTemplate jdbcTemplate;

  /** Creates an absolute URL for an endpoint path. */
  protected String url(String path) {
    return "http://localhost:" + port + path;
  }

  /** Creates headers authenticated as the default test caseworker. */
  protected HttpHeaders headers() {
    return headersFor(TestJwtDecoderConfig.CASEWORKER_ID);
  }

  /** Creates headers authenticated as the supplied test caseworker. */
  protected HttpHeaders headersFor(UUID caseworkerId) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(
        caseworkerId.equals(TestJwtDecoderConfig.OTHER_CASEWORKER_ID)
            ? TestJwtDecoderConfig.OTHER_BEARER_TOKEN
            : TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }
}

package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.default_schema=PUBLIC",
      "spring.datasource.url=jdbc:h2:mem:axon-dev-token;DB_CLOSE_DELAY=-1",
      "feature.disable-security=false",
      "feature.enable-dev-token=true",
      "ENTRA_ISSUER_URI=" + TestJwtDecoderConfig.ISSUER_URI,
      "ENTRA_AUD=" + TestJwtDecoderConfig.AUDIENCE
    })
@AutoConfigureTestRestTemplate
class DevTokenInMemoryTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired
  private AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver;

  @Test
  void givenSwaggerCaseworkerToken_whenGetUnknownPriorAuthority_thenReturnsNotFound() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.setBearerAuth("swagger-caseworker-token");

    ResponseEntity<String> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/prior-authorities/" + UUID.randomUUID(),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenSwaggerProviderToken_whenGetUnknownPriorAuthority_thenReturnsNotFound() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.setBearerAuth("swagger-provider-token");

    ResponseEntity<String> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/prior-authorities/" + UUID.randomUUID(),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenSwaggerCaseworkerTokens_whenAuthenticated_thenEachHasItsConfiguredEntraOid() {
    assertThat(authenticate("swagger-caseworker-token").getClaimAsString("oid"))
        .isEqualTo("00000000-0000-0000-0000-000000000001");
    assertThat(authenticate("swagger-caseworker-token-2").getClaimAsString("oid"))
        .isEqualTo("00000000-0000-0000-0000-000000000002");
  }

  private Jwt authenticate(String token) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    Authentication authentication =
        authenticationManagerResolver
            .resolve(request)
            .authenticate(new BearerTokenAuthenticationToken(token));
    return ((JwtAuthenticationToken) authentication).getToken();
  }
}

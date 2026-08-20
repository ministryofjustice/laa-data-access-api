package uk.gov.justice.laa.dstew.access.testsupport;

import java.util.List;
import java.util.Map;
import org.springframework.boot.restclient.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import uk.gov.laa.springboot.oauth2.testsupport.StubJwtDecoder;
import uk.gov.laa.springboot.oauth2.testsupport.StubJwtToken;

/**
 * Shared starter-backed JWT support for Spring Boot tests.
 *
 * <p>Provides a deterministic bearer token that the real starter security filter chain can decode
 * without a mock OAuth2 server.
 */
@Configuration
public class TestJwtDecoderConfig {

  public static final String BEARER_TOKEN = "test-caseworker-token";
  public static final String ISSUER_URI = "https://issuer.example.test";
  public static final String AUDIENCE = "api://data-access-api-test";

  @Bean
  @Primary
  JwtDecoder jwtDecoder() {
    return StubJwtDecoder.of(
        new StubJwtToken(
            BEARER_TOKEN,
            "caseworker@example.com",
            new String[] {"LAA_CASEWORKER"},
            null,
            Map.of("iss", ISSUER_URI, "aud", List.of(AUDIENCE))));
  }

  @Bean
  RestTemplateCustomizer bearerTokenRestTemplateCustomizer() {
    return restTemplate ->
        restTemplate
            .getInterceptors()
            .add(
                (request, body, execution) -> {
                  request.getHeaders().setBearerAuth(BEARER_TOKEN);
                  return execution.execute(request, body);
                });
  }
}

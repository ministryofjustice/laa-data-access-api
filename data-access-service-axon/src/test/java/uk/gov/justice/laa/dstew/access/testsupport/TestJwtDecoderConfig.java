package uk.gov.justice.laa.dstew.access.testsupport;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
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
@TestConfiguration
public class TestJwtDecoderConfig {

  public static final String BEARER_TOKEN = "test-caseworker-token";
  public static final String OTHER_BEARER_TOKEN = "other-test-caseworker-token";
  public static final UUID CASEWORKER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  public static final UUID OTHER_CASEWORKER_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
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
            Map.of("iss", ISSUER_URI, "aud", List.of(AUDIENCE), "oid", CASEWORKER_ID.toString())),
        new StubJwtToken(
            OTHER_BEARER_TOKEN,
            "other-caseworker@example.com",
            new String[] {"LAA_CASEWORKER"},
            null,
            Map.of(
                "iss",
                ISSUER_URI,
                "aud",
                List.of(AUDIENCE),
                "oid",
                OTHER_CASEWORKER_ID.toString())));
  }
}

package uk.gov.justice.laa.dstew.access.config;

import java.time.Instant;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import uk.gov.justice.laa.dstew.access.security.TokenProvider;

/**
 * Test configuration that provides a mocked TokenProvider for integration tests. This prevents the
 * real OAuth2 client credentials flow from running during tests, which would fail without a live
 * identity provider.
 */
@TestConfiguration
public class TokenTestConfiguration {

  @Bean
  @Primary
  public TokenProvider mockTokenProvider() {
    TokenProvider mock = Mockito.mock(TokenProvider.class);
    OAuth2AccessToken token =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "test-bearer-token",
            Instant.now(),
            Instant.now().plusSeconds(3600));
    Mockito.when().command(mock.getTokenFromProvider()).thenReturn(token);
    return mock;
  }
}

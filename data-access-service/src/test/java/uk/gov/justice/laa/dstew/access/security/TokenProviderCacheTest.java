package uk.gov.justice.laa.dstew.access.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

@SpringJUnitConfig(TokenProviderCacheTest.CacheTestConfig.class)
class TokenProviderCacheTest {

  @MockitoBean OAuth2AuthorizedClientManager authorizedClientManager;

  @Autowired TokenProvider tokenProvider;

  @Autowired CacheManager cacheManager;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(tokenProvider, "clientRegistrationId", "test-sds-client-id");
    ReflectionTestUtils.setField(tokenProvider, "principalName", "test-principal");
    cacheManager.getCache(TokenProvider.CACHE_NAME).clear();

    OAuth2AuthorizedClient authorizedClient = mock(OAuth2AuthorizedClient.class);
    OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
    when(authorizedClientManager.authorize(any(OAuth2AuthorizeRequest.class)))
        .thenReturn(authorizedClient);
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
  }

  @Test
  void givenTokenAlreadyCached_whenGetTokenFromProviderCalledAgain_thenAuthorizeCalledOnce() {
    tokenProvider.getTokenFromProvider();
    tokenProvider.getTokenFromProvider();

    verify(authorizedClientManager, times(1)).authorize(any(OAuth2AuthorizeRequest.class));
  }

  @Test
  void givenCacheEvicted_whenGetTokenFromProviderCalledAgain_thenAuthorizeCalledAgain() {
    tokenProvider.getTokenFromProvider();
    tokenProvider.evictToken();
    tokenProvider.getTokenFromProvider();

    verify(authorizedClientManager, times(2)).authorize(any(OAuth2AuthorizeRequest.class));
  }

  @EnableCaching
  @Configuration
  static class CacheTestConfig {

    @Bean
    TokenProvider tokenProvider(OAuth2AuthorizedClientManager authorizedClientManager) {
      return new TokenProvider(authorizedClientManager);
    }

    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager(TokenProvider.CACHE_NAME);
    }
  }
}
